package com.tutor.app.ame.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CountryCatalogService {

    private final Map<String, CountryProfile> countries = new LinkedHashMap<>();

    public CountryCatalogService() {
        countries.put("Nigeria", new CountryProfile("Nigeria", "NGN"));
        countries.put("Ghana", new CountryProfile("Ghana", "GHS"));
        countries.put("Benin", new CountryProfile("Benin", "XOF"));
        countries.put("Burkina Faso", new CountryProfile("Burkina Faso", "XOF"));
        countries.put("Cape Verde", new CountryProfile("Cape Verde", "CVE"));
        countries.put("Cote d'Ivoire", new CountryProfile("Cote d'Ivoire", "XOF"));
        countries.put("Gambia", new CountryProfile("Gambia", "GMD"));
        countries.put("Guinea", new CountryProfile("Guinea", "GNF"));
        countries.put("Guinea-Bissau", new CountryProfile("Guinea-Bissau", "XOF"));
        countries.put("Liberia", new CountryProfile("Liberia", "LRD"));
        countries.put("Mali", new CountryProfile("Mali", "XOF"));
        countries.put("Niger", new CountryProfile("Niger", "XOF"));
        countries.put("Senegal", new CountryProfile("Senegal", "XOF"));
        countries.put("Sierra Leone", new CountryProfile("Sierra Leone", "SLE"));
        countries.put("Togo", new CountryProfile("Togo", "XOF"));
        countries.put("Kenya", new CountryProfile("Kenya", "KES"));
        countries.put("Uganda", new CountryProfile("Uganda", "UGX"));
        countries.put("Tanzania", new CountryProfile("Tanzania", "TZS"));
        countries.put("Ethiopia", new CountryProfile("Ethiopia", "ETB"));
        countries.put("South Africa", new CountryProfile("South Africa", "ZAR"));
    }

    public Map<String, CountryProfile> all() {
        return countries;
    }

    public Optional<CountryProfile> get(String country) {
        return Optional.ofNullable(countries.get(country));
    }

    public record CountryProfile(String name, String currencyCode) {
    }
}
