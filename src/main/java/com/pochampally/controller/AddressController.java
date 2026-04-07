package com.pochampally.controller;

import com.pochampally.entity.Address;
import com.pochampally.service.AddressService;
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

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<List<Address>> list(Authentication auth) {
        return ResponseEntity.ok(addressService.listByUser(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<Address> create(@RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(addressService.create(auth.getName(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Address> update(@PathVariable String id, @RequestBody Map<String, String> body,
                                           Authentication auth) {
        return ResponseEntity.ok(addressService.update(id, auth.getName(), body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        addressService.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Address> setDefault(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(addressService.setDefault(id, auth.getName()));
    }
}
