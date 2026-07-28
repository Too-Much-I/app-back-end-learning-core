package web.tosunsaeng.global.config.auth;

import org.springframework.core.convert.converter.Converter;

public class AuthModeConverter implements Converter<String, AuthMode> {

    @Override
    public AuthMode convert(String source) {
        return AuthMode.fromProperty(source);
    }
}
