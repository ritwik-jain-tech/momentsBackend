package com.moments.service;

import com.moments.models.MessageCentralSendResponse;
import com.moments.models.MessageCentralVerifyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MessageCentralService {

    private final RestTemplate restTemplate;

    @Value("${messagecentral.base.url}")
    private String baseUrl;

    @Value("${messagecentral.auth.token}")
    private String authToken;

    @Value("${messagecentral.customer.id}")
    private String customerId;

    @Value("${messagecentral.default.country.code}")
    private String defaultCountryCode;

    @Value("${messagecentral.flow.type}")
    private String flowType;

    public MessageCentralService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Send OTP to the given phone number
     * @param phoneNumber Phone number without country code
     * @param countryCode Country code (defaults to configured value)
     * @return verificationId from the response
     * @throws Exception if API call fails
     */
    public String sendOtp(String phoneNumber, String countryCode) throws Exception {
        String countryCodeToUse = countryCode != null && !countryCode.isEmpty() ? countryCode : defaultCountryCode;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/send")
                .queryParam("countryCode", countryCodeToUse)
                .queryParam("customerId", customerId)
                .queryParam("flowType", flowType)
                .queryParam("mobileNumber", phoneNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authToken", authToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<MessageCentralSendResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    entity,
                    MessageCentralSendResponse.class
            );

            MessageCentralSendResponse sendResponse = response.getBody();
            if (sendResponse != null && sendResponse.getData() != null) {
                return sendResponse.getData().getVerificationId();
            } else {
                throw new Exception("Failed to get verificationId from MessageCentral response");
            }
        } catch (Exception e) {
            throw new Exception("Error calling MessageCentral send OTP API: " + e.getMessage(), e);
        }
    }

    /**
     * Verify OTP with the given verification ID and code
     * @param phoneNumber Phone number without country code
     * @param verificationId Verification ID from send OTP response
     * @param code OTP code to verify
     * @param countryCode Country code (defaults to configured value)
     * @return true if OTP is verified successfully
     * @throws Exception if API call fails
     */
    public MessageCentralVerifyResponse verifyOtp(String phoneNumber, String verificationId, String code, String countryCode) throws Exception {
        String countryCodeToUse = countryCode != null && !countryCode.isEmpty() ? countryCode : defaultCountryCode;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/validateOtp")
                .queryParam("countryCode", countryCodeToUse)
                .queryParam("mobileNumber", phoneNumber)
                .queryParam("verificationId", verificationId)
                .queryParam("customerId", customerId)
                .queryParam("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authToken", authToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<MessageCentralVerifyResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    MessageCentralVerifyResponse.class
            );

             return response.getBody();

        } catch (Exception e) {
            throw new Exception("Error calling MessageCentral verify OTP API: " + e.getMessage(), e);
        }
    }
}

