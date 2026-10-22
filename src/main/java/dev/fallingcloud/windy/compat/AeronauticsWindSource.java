package dev.fallingcloud.windy.compat;

import com.enzonic.aeronauticswinds.WindsSim;
import com.enzonic.aeronauticswinds.weather.WeatherType;
import dev.fallingcloud.windy.WindMath;
import dev.fallingcloud.windy.WindyConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Reads the same client-side wind sample Aeronautics uses for its own particle drift.
 *
 * <p>This class deliberately touches Aeronautics types only from {@link #sample}; it is instantiated
 * behind a mod-list guard in {@link WindSources}, so Windy still loads in packs without Aeronautics.
 * The API used here is public and unobfuscated in Aeronautics 1.3.x but is not a formal API contract;
 * {@link WindSources} catches linkage failures and falls back to vanilla wind.
 */
public final class AeronauticsWindSource implements WindSource {
    private static final long WEATHER_CACHE_TICKS = 20L;
    private static final double WEATHER_CACHE_DISTANCE_SQR = 16.0 * 16.0;

    private final Vector3d vector = new Vector3d();

    private WeatherType cachedWeather = WeatherType.NONE;
    private long cachedWeatherTick = Long.MIN_VALUE;
    private double cachedWeatherX;
    private double cachedWeatherZ;
    private boolean weatherValid;

    @Override
    public Sample sample(ClientLevel level, Vec3 position) {
        WindsSim.Simulation simulation = WindsSim.forLevel(level);
        simulation.sampleWindTo(level, position.x, position.y, position.z, vector);

        double speedMps = vector.length();
        float yawToward = WindMath.yawToward(vector.x, vector.z);
        float strength = WindMath.visualStrength(speedMps,
                WindyConfig.get().aeronauticsFullStrengthMps());
        float gust = Math.min(1.4f, strength * 0.9f);

        WindSource.Weather weather = null;
        if (WindyConfig.get().aeronauticsWeather()) {
            weather = weatherOf(weatherAt(simulation, level, position));
        }
        return new Sample(yawToward, strength, gust, speedMps, weather);
    }

    private WeatherType weatherAt(WindsSim.Simulation simulation, ClientLevel level, Vec3 position) {
        long now = level.getGameTime();
        double dx = position.x - cachedWeatherX;
        double dz = position.z - cachedWeatherZ;
        boolean stale = !weatherValid
                || now < cachedWeatherTick
                || now - cachedWeatherTick >= WEATHER_CACHE_TICKS
                || dx * dx + dz * dz > WEATHER_CACHE_DISTANCE_SQR;
        if (stale) {
            cachedWeather = simulation.weatherAt(level, position.x, position.z);
            cachedWeatherTick = now;
            cachedWeatherX = position.x;
            cachedWeatherZ = position.z;
            weatherValid = true;
        }
        return cachedWeather;
    }

    private static WindSource.Weather weatherOf(WeatherType type) {
        return new WindSource.Weather(
                type.isWet(),
                type.isFrozen(),
                type.isSandy(),
                type.isHot(),
                type.isStormy(),
                type.isFoggy(),
                type.name().toLowerCase(java.util.Locale.ROOT));
    }
}
