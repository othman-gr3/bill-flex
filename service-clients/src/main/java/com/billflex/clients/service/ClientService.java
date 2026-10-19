package com.billflex.clients.service;

import com.billflex.clients.dto.ClientRequest;
import com.billflex.clients.dto.ClientResponse;
import com.billflex.clients.exception.DuplicateResourceException;
import com.billflex.clients.exception.ResourceNotFoundException;
import com.billflex.clients.model.Client;
import com.billflex.clients.model.StatutClient;
import com.billflex.clients.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository repository;

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return repository.findAll().stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(Long id) {
        return ClientResponse.from(getOrThrow(id));
    }

    public ClientResponse create(ClientRequest r) {
        if (repository.existsByEmail(r.email())) {
            throw new DuplicateResourceException("Un client avec l'email " + r.email() + " existe déjà");
        }
        Client client = Client.builder()
                .nom(r.nom())
                .email(r.email())
                .numeroTVA(r.numeroTVA())
                .modePaiement(r.modePaiement())
                .statut(StatutClient.ACTIF)
                .build();
        return ClientResponse.from(repository.save(client));
    }

    public ClientResponse update(Long id, ClientRequest r) {
        Client client = getOrThrow(id);
        if (repository.existsByEmailAndIdNot(r.email(), id)) {
            throw new DuplicateResourceException("Un client avec l'email " + r.email() + " existe déjà");
        }
        client.setNom(r.nom());
        client.setEmail(r.email());
        client.setNumeroTVA(r.numeroTVA());
        client.setModePaiement(r.modePaiement());
        return ClientResponse.from(repository.save(client));
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
        repository.flush(); // forces the FK check now, so a referenced client gives a clean 409
    }

    private Client getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable avec l'id " + id));
    }
}
