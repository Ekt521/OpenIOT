package com.openiot.platform.web;

import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.service.ProductService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.ProductRequest;
import com.openiot.platform.web.dto.StateResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService products;
  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public ProductController(
      ProductService products,
      StateAssembler assembler,
      CurrentUserService currentUser) {
    this.products = products;
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @PostMapping
  public StateResponse create(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @RequestBody ProductRequest request) {
    products.create(request);
    return assembler.build(currentUser.toUser(email));
  }

  @PutMapping("/{id}")
  public StateResponse update(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody ProductRequest request) {
    products.update(id, request);
    return assembler.build(currentUser.toUser(email));
  }

  @DeleteMapping("/{id}")
  public StateResponse delete(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id) {
    products.delete(id);
    return assembler.build(currentUser.toUser(email));
  }
}
