package com.homebase.mini.product.configuration.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.BodyTypeSelector;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.annotation.ChenileParamType;
import org.chenile.http.handler.ControllerSupport;
import org.springframework.http.ResponseEntity;

import org.chenile.stm.StateEntity;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import com.homebase.mini.product.model.Product;

@RestController
@ChenileController(value = "productService", serviceName = "_productStateEntityService_", healthCheckerName = "productHealthChecker")
public class ProductController extends ControllerSupport {

	@GetMapping("/product/{id}")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Product>>> retrieve(
			HttpServletRequest httpServletRequest,
			@PathVariable String id) {
		return process(httpServletRequest, id);
	}

	@PostMapping("/product")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Product>>> create(
			HttpServletRequest httpServletRequest,
			@ChenileParamType(StateEntity.class) @RequestBody Product entity) {
		return process(httpServletRequest, entity);
	}

	@PatchMapping("/product/{id}/{eventID}")
	@BodyTypeSelector("productBodyTypeSelector")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Product>>> processById(
			HttpServletRequest httpServletRequest,
			@PathVariable String id,
			@PathVariable String eventID,
			@ChenileParamType(Object.class) @RequestBody String eventPayload) {
		return process(httpServletRequest, id, eventID, eventPayload);
	}

}
