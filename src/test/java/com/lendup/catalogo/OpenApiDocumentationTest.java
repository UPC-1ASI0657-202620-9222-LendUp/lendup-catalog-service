package com.lendup.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica que el contrato HTTP sea público sin abrir las escrituras. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired MockMvc mvc;

    @Test
    void publicaOpenApiSinToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LendUp - API de Catálogo"))
                .andExpect(jsonPath("$.paths").isMap())
                .andExpect(jsonPath("$.components.securitySchemes.firebaseBearer").exists());
    }
}
