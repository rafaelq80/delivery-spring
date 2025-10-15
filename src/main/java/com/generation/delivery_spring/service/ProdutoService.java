package com.generation.delivery_spring.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.generation.delivery_spring.model.Produto;
import com.generation.delivery_spring.repository.CategoriaRepository;
import com.generation.delivery_spring.repository.ProdutoRepository;

@Service
public class ProdutoService {

	private static final Logger logger = LoggerFactory.getLogger(NutriScoreService.class);

	@Autowired
	private ProdutoRepository produtoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private NutriScoreService nutriScoreService;

	public List<Produto> findAll() {
		return produtoRepository.findAll();
	}

	public Optional<Produto> findById(Long id) {
		return produtoRepository.findById(id);
	}

	public List<Produto> findAllByNome(String nome) {
		return produtoRepository.findAllByNomeContainingIgnoreCase(nome);
	}

	public List<Produto> findAllByNutriscore() {
		return produtoRepository.findAllByNutriscoreIn(List.of("A", "B"));
	}
	
	public Produto cadastrar(Produto produto) {

		if (produto == null) {
			throw new IllegalArgumentException("Produto não pode ser nulo");
		}

		if (produto.getCategoria() != null && !categoriaRepository.existsById(produto.getCategoria().getId())) {
			throw new IllegalArgumentException("Categoria inválida");
		}

		definirNutriscore(produto);

		return produtoRepository.save(produto);

	}

	public Optional<Produto> atualizar(Produto produto) {

		if (produto == null || produto.getId() == null) {
			throw new IllegalArgumentException("O Produto não pode ser nulo");
		}

		if (produto.getCategoria() == null || produto.getCategoria().getId() == null) {
			throw new IllegalArgumentException("A Categoria não pode ser nula");
		}

		if (produtoRepository.existsById(produto.getId())
				&& categoriaRepository.existsById(produto.getCategoria().getId())) {
			
			definirNutriscore(produto);
			
			return Optional.of(produtoRepository.save(produto));
		}

		return Optional.empty();
	}

	public Boolean delete(Long id) {
		if (produtoRepository.existsById(id)) {
			produtoRepository.deleteById(id);
			return true;
		}

		return false;
	}
	
	private void definirNutriscore(Produto produto) {
	    try {
	        // Chama o serviço e obtém o resultado do NutriScore
	        var resultado = nutriScoreService.pesquisarNutriScore(produto.getNome());

	        // Acessa a classificação diretamente do record ResultadoNutriScore
	        if (resultado != null && resultado.classificacao() != null && !resultado.classificacao().isBlank()) {
	            produto.setNutriscore(resultado.classificacao());
	        } else {
	            produto.setNutriscore("");
	        }

	    } catch (Exception e) {
	        logger.error("Erro ao calcular NutriScore para o produto: {}", produto.getNome(), e);
	        produto.setNutriscore(""); // Garante valor padrão em caso de erro
	    }
	}

}
