package com.lendup.catalogo.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activación explícita evita que las pruebas y lecturas públicas requieran secretos.
 * Nunca se incluyen service accounts o tokens Firebase dentro del repositorio.
 */
@Configuration
@ConditionalOnProperty(prefix = "catalogo.security", name = "firebase-enabled", havingValue = "true")
public class FirebaseAdminConfig {
    @Bean
    FirebaseApp catalogoFirebaseApp(@Value("${firebase.project-id:}") String projectId)
            throws IOException {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID es obligatorio");
        }
        GoogleCredentials credentials;
        if (System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null) {
            credentials = GoogleCredentials.create(new AccessToken("emulator-only",
                    new Date(System.currentTimeMillis() + 86_400_000L)));
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        synchronized (FirebaseApp.class) {
            for (var app : FirebaseApp.getApps()) {
                if ("lendup-catalogo".equals(app.getName())) return app;
            }
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials).setProjectId(projectId).build(), "lendup-catalogo");
        }
    }

    @Bean
    FirebaseAuth catalogoFirebaseAuth(FirebaseApp catalogoFirebaseApp) {
        return FirebaseAuth.getInstance(catalogoFirebaseApp);
    }
}
