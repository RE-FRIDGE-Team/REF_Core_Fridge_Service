package com.refridge.core_server.product.domain;


import com.refridge.core_server.product.domain.ar.REFProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface REFProductRepository extends JpaRepository<REFProduct, Long>, REFProductRepositoryCustom {
}
