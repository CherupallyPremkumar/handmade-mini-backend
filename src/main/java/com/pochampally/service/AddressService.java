package com.pochampally.service;

import com.pochampally.entity.Address;
import com.pochampally.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;
    private final SettingsService settingsService;

    public List<Address> listByUser(String userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedTimeDesc(userId);
    }

    public Address getByIdAndUser(String id, String userId) {
        return addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    }

    @Transactional
    public Address create(String userId, Map<String, String> body) {
        int maxAddresses = settingsService.getInt("max_addresses_per_user");
        long count = addressRepository.countByUserId(userId);
        if (count >= maxAddresses) {
            throw new IllegalStateException("Maximum " + maxAddresses + " addresses allowed");
        }

        validate(body);

        boolean isFirst = count == 0;
        boolean setDefault = isFirst || "true".equals(body.get("isDefault"));

        if (setDefault) {
            addressRepository.clearDefaultForUser(userId);
        }

        Address address = Address.builder()
                .userId(userId)
                .label(body.getOrDefault("label", ""))
                .name(body.get("name"))
                .phone(body.get("phone"))
                .line1(body.get("line1"))
                .line2(body.getOrDefault("line2", ""))
                .city(body.get("city"))
                .state(body.get("state"))
                .pincode(body.get("pincode"))
                .isDefault(setDefault)
                .build();

        return addressRepository.save(address);
    }

    @Transactional
    public Address update(String id, String userId, Map<String, String> body) {
        Address address = getByIdAndUser(id, userId);

        if (body.containsKey("name")) address.setName(body.get("name"));
        if (body.containsKey("phone")) address.setPhone(body.get("phone"));
        if (body.containsKey("line1")) address.setLine1(body.get("line1"));
        if (body.containsKey("line2")) address.setLine2(body.get("line2"));
        if (body.containsKey("city")) address.setCity(body.get("city"));
        if (body.containsKey("state")) address.setState(body.get("state"));
        if (body.containsKey("pincode")) address.setPincode(body.get("pincode"));
        if (body.containsKey("label")) address.setLabel(body.get("label"));

        if ("true".equals(body.get("isDefault"))) {
            addressRepository.clearDefaultForUser(userId);
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    @Transactional
    public void delete(String id, String userId) {
        Address address = getByIdAndUser(id, userId);
        addressRepository.delete(address);
    }

    @Transactional
    public Address setDefault(String id, String userId) {
        Address address = getByIdAndUser(id, userId);
        addressRepository.clearDefaultForUser(userId);
        address.setIsDefault(true);
        return addressRepository.save(address);
    }

    private void validate(Map<String, String> body) {
        String name = body.get("name");
        String phone = body.get("phone");
        String line1 = body.get("line1");
        String city = body.get("city");
        String state = body.get("state");
        String pincode = body.get("pincode");

        if (name == null || name.isBlank() || phone == null || phone.isBlank() ||
            line1 == null || line1.isBlank() || city == null || city.isBlank() ||
            state == null || state.isBlank() || pincode == null || pincode.isBlank()) {
            throw new IllegalArgumentException("name, phone, line1, city, state, pincode are required");
        }
    }
}
