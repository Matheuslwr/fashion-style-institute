package br.com.fsistudio.store.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

@Component
public class ShippingEstimator {
    private static final double ORIGIN_LATITUDE = -25.4284;
    private static final double ORIGIN_LONGITUDE = -49.2733;
    private final RestClient client;

    public ShippingEstimator(@Value("${app.shipping.cep-api:https://brasilapi.com.br}") String cepApi) {
        this.client = RestClient.builder().baseUrl(cepApi).build();
    }

    public Quote quote(String postalCode, String requestedCity, String requestedState) {
        JsonNode address;
        try {
            address = client.get().uri("/api/cep/v2/{postalCode}", postalCode).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CEP informado não foi encontrado", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível validar o CEP. Tente novamente.", exception);
        }
        if (address == null || address.path("city").asText().isBlank() || address.path("state").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CEP informado não foi encontrado");
        }
        String city = address.path("city").asText();
        String state = address.path("state").asText();
        if (!city.equalsIgnoreCase(requestedCity.trim()) || !state.equalsIgnoreCase(requestedState.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cidade ou o estado não correspondem ao CEP");
        }
        JsonNode coordinates = address.path("location").path("coordinates");
        double latitude = coordinates.path("latitude").asDouble(Double.NaN);
        double longitude = coordinates.path("longitude").asDouble(Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível calcular a estimativa de entrega para este CEP");
        }

        int distanceKm = (int) Math.round(distanceKm(latitude, longitude) * 1.25);
        long shippingCents = Math.round((990 + distanceKm * 1.8) / 10) * 10;
        return new Quote(city, state, distanceKm, shippingCents);
    }

    private static double distanceKm(double latitude, double longitude) {
        double latitudeDelta = Math.toRadians(latitude - ORIGIN_LATITUDE);
        double longitudeDelta = Math.toRadians(longitude - ORIGIN_LONGITUDE);
        double originLatitude = Math.toRadians(ORIGIN_LATITUDE);
        double destinationLatitude = Math.toRadians(latitude);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(originLatitude) * Math.cos(destinationLatitude)
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    public record Quote(String city, String state, int distanceKm, long shippingCents) { }
}
