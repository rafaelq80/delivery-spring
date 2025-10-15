package com.generation.delivery_spring.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.generation.delivery_spring.model.Categoria;
import com.generation.delivery_spring.repository.CategoriaRepository;

import jakarta.validation.Valid;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    public List<Categoria> buscarPorDescricao(String descricao) {
        return categoriaRepository.findAllByDescricaoContainingIgnoreCase(descricao);
    }

    public Categoria criar(@Valid Categoria categoria) {
        return categoriaRepository.save(categoria);
    }

    public Categoria atualizar(@Valid Categoria categoria) {
        if (!categoriaRepository.existsById(categoria.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada");
        }
        return categoriaRepository.save(categoria);
    }

    public void deletar(Long id) {
        Optional<Categoria> categoria = categoriaRepository.findById(id);
        if (categoria.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada");
        }
        categoriaRepository.deleteById(id);
    }
}
