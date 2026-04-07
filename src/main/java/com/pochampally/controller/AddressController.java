package com.pochampally.controller;

import com.pochampally.entity.Address;
import com.pochampally.repository.AddressRepository;
import com.pochampally.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressRepository addressRepository;
    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<Address>> list(Authentication auth) {
        return ResponseEntity.ok(addressRepository.findByUserIdOrderByIsDefaultDescCreatedTimeDesc(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        String userId = auth.getName();

        int maxAddresses = settingsService.getInt("max_addresses_per_user");
        if (addressRepository.countByUserId(userId) >= maxAddresses) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum " + maxAddresses + " addresses allowed"));
        }

        String name = body.get("name");
        String phone = body.get("phone");
        String line1 = body.get("line1");
        String city = body.get("city");
        String state = body.get("state");
        String pincode = body.get("pincode");

        if (name == null || name.isBlank() || phone == null || phone.isBlank() ||
            line1 == null || line1.isBlank() || city == null || city.isBlank() ||
            state == null || state.isBlank() || pincode == null || pincode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name, phone, line1, city, state, pincode are required"));
        }

        boolean isFirst = addressRepository.countByUserId(userId) == 0;

        Address address = Address.builder()
                .userId(userId)
                .label(body.getOrDefault("label", ""))
                .name(name)
                .phone(phone)
                .line1(line1)
                .line2(body.getOrDefault("line2", ""))
                .city(city)
                .state(state)
                .pincode(pincode)
                .isDefault(isFirst || "true".equals(body.get("isDefault")))
                .build();

        // If this is set as default, unset others
        if (address.getIsDefault()) {
            unsetDefaultForUser(userId);
        }

        return ResponseEntity.ok(addressRepository.save(address));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, String> body, Authentication auth) {
        Address address = addressRepository.findByIdAndUserId(id, auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (body.containsKey("name")) address.setName(body.get("name"));
        if (body.containsKey("phone")) address.setPhone(body.get("phone"));
        if (body.containsKey("line1")) address.setLine1(body.get("line1"));
        if (body.containsKey("line2")) address.setLine2(body.get("line2"));
        if (body.containsKey("city")) address.setCity(body.get("city"));
        if (body.containsKey("state")) address.setState(body.get("state"));
        if (body.containsKey("pincode")) address.setPincode(body.get("pincode"));
        if (body.containsKey("label")) address.setLabel(body.get("label"));

        if ("true".equals(body.get("isDefault"))) {
            unsetDefaultForUser(auth.getName());
            address.setIsDefault(true);
        }

        return ResponseEntity.ok(addressRepository.save(address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        Address address = addressRepository.findByIdAndUserId(id, auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        addressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Address> setDefault(@PathVariable String id, Authentication auth) {
        Address address = addressRepository.findByIdAndUserId(id, auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        unsetDefaultForUser(auth.getName());
        address.setIsDefault(true);
        return ResponseEntity.ok(addressRepository.save(address));
    }

    private void unsetDefaultForUser(String userId) {
        for (Address a : addressRepository.findByUserIdOrderByIsDefaultDescCreatedTimeDesc(userId)) {
            if (a.getIsDefault()) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        }
    }
}
