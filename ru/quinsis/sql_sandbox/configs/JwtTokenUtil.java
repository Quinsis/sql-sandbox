package ru.quinsis.sql_sandbox.configs;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;

public class JwtTokenUtil {
    @Getter
    private static final String secret = "348ut1fhd3n90uj4tnm3u9fh81tu13nfdm38f9dr193jg35ng8n34jq934n9ugn35jngq39jg2";

    public static String generateToken(String id) {
        return Jwts.builder()
                .setSubject(id)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes())
                .compact();
    }
}

