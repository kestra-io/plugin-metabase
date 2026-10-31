package io.kestra.plugin.metabase;

import lombok.Getter;

@Getter
public class MetabaseApiException extends RuntimeException {
    private final int statusCode;

    public MetabaseApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
