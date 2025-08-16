package de.cuioss.benchmarking.common.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.experimental.UtilityClass;

import java.time.Instant;

/**
 * Provides a configured {@link Gson} instance for the benchmarking framework.
 */
@UtilityClass
public class GsonProvider {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .setPrettyPrinting()
            .create();

    /**
     * @return a configured {@link Gson} instance.
     */
    public static Gson getGson() {
        return GSON;
    }
}
