package com.generation.delivery_spring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generation.delivery_spring.exception.GeminiApiException;

import io.github.cdimascio.dotenv.Dotenv;

@Service
public class NutriScoreService {
	private static final Logger logger = LoggerFactory.getLogger(NutriScoreService.class);

	private static final Dotenv dotenv = Dotenv.load();

	private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?";
	private static final String API_KEY = dotenv.get("API_KEY");

	private final RestTemplate restTemplate;

	// Nutrient ranges for scoring
	private final Map<String, double[][]> nutrientRanges = new HashMap<String, double[][]>() {

		private static final long serialVersionUID = 1L;

		{
			put("energia", new double[][] { { 335 }, { 670 }, { 1005 }, { 1340 }, { 1675 }, { 2010 }, { 2345 },
					{ 2680 }, { 3015 }, { 3350 } });
			put("acucar", new double[][] { { 4.5 }, { 9 }, { 13.5 }, { 18 }, { 22.5 } });
			put("gordurasSaturadas", new double[][] { { 1 }, { 2 }, { 3 }, { 4 }, { 5 } });
			put("sodio", new double[][] { { 90 }, { 180 }, { 270 }, { 360 }, { 450 } });
			put("proteinas", new double[][] { { 4.8 }, { 6.4 }, { 8 } });
			put("fibras", new double[][] { { 2.8 }, { 3.7 }, { 4.7 } });
			put("frutasLegumesOleaginosas", new double[][] { { 10 }, { 20 }, { 40 }, { 60 }, { 80 } });
		}
	};

	// Nutrient extraction patterns
	private final Map<String, String> nutrientPatterns = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;

		{
			put("valorEnergetico", "Valor energético.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*kcal");
			put("acucaresTotais", "Açúcares totais.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*g");
			put("gordurasSaturadas", "Gorduras saturadas.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*g");
			put("sodio", "Sódio.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*mg");
			put("proteinas", "Proteínas.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*g");
			put("fibrasAlimentares", "Fibras alimentares.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*g");
			put("percentualFrutasLegumesOleaginosas",
					"% de frutas, legumes e oleaginosas.*?(\\d+(?:,\\d+)?(?:\\s*-\\s*\\d+(?:,\\d+)?)?)\s*%");
		}
	};

	public NutriScoreService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public String pesquisarNutriScore(String produto) throws Exception {
		// Criar prompt para Gemini
		String prompt = criarPrompt(produto.trim());

		// Chamar API Gemini
		String resposta = chamarGeminiAPI(prompt);
		logger.info("Resposta da API: {}", resposta);

		// Extrair dados nutricionais
		Map<String, Double> dadosNutricionais = extrairDadosNutricionais(resposta);
		logger.info("Dados Nutricionais: {}", dadosNutricionais);

		// Calcular NutriScore
		return calcularNutriScore(dadosNutricionais);
	}

	private String criarPrompt(String produto) {
		return String.format("Forneça informações nutricionais médias por 100g do prato %s. "
				+ "Inclua: Valor energético (kcal), Açúcares totais (g), Gorduras saturadas (g), "
				+ "Sódio (mg), Proteínas (g), Fibras alimentares (g), " + "%% de frutas, legumes e oleaginosas. "
				+ "Se possível, baseie-se em fontes confiáveis, como tabelas nutricionais oficiais "
				+ "ou informações de rótulos de produtos similares. "
				+ "Caso haja variações dependendo do preparo, forneça uma média geral. "
				+ "Não traga as informações nutricionais na forma de tabela.", produto);
	}
	
	private String chamarGeminiAPI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(
            Map.of("parts", List.of(
                Map.of("text", prompt)
            ))
        ));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            String response = restTemplate.postForObject(
                GEMINI_API_URL + "key=" + API_KEY, 
                request, 
                String.class
            );
            
            logger.info(response);
            
            // Validação da resposta
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            
            // Verificação de conteúdo válido
            if (!root.has("candidates") || root.path("candidates").isEmpty()) {
                logger.warn("Nenhuma resposta válida recebida da API Gemini");
                throw new GeminiApiException("Sem respostas válidas", HttpStatus.NO_CONTENT);
            }
            
            // Extração do texto da resposta
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
        } catch (HttpClientErrorException e) {
            // Erros de cliente (4xx)
            logger.error("Erro de cliente na chamada da Gemini API", e);
            throw new GeminiApiException("Erro de cliente: " + e.getStatusText(), e.getStatusCode());

        } catch (HttpServerErrorException e) {
            // Erros de servidor (5xx)
            logger.error("Erro de servidor na chamada da Gemini API", e);
            throw new GeminiApiException("Erro interno do servidor", HttpStatus.INTERNAL_SERVER_ERROR);

        } catch (JsonProcessingException e) {
            // Erro de parsing do JSON
            logger.error("Erro ao processar resposta JSON", e);
            throw new GeminiApiException("Erro no processamento da resposta", HttpStatus.UNPROCESSABLE_ENTITY);

        } catch (RestClientException e) {
            // Outros erros de comunicação REST
            logger.error("Erro de comunicação com a API Gemini", e);
            throw new GeminiApiException("Falha na comunicação", HttpStatus.SERVICE_UNAVAILABLE);

        } catch (Exception e) {
            // Tratamento de exceções inesperadas
            logger.error("Erro inesperado ao processar resposta do Gemini", e);
            throw new GeminiApiException("Erro inesperado", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	private Map<String, Double> extrairDadosNutricionais(String resposta) {
		Map<String, Double> dadosNutricionais = new HashMap<>();

		// Extrai cada nutriente usando os padrões definidos
		nutrientPatterns.forEach((nutriente, padrao) -> {
			double valor = extrairValor(resposta, padrao);
			dadosNutricionais.put(nutriente, valor);
		});

		return dadosNutricionais;
	}

	private double extrairValor(String texto, String padrao) {
		Pattern pattern = Pattern.compile(padrao, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(texto);

		if (matcher.find()) {
			String valorStr = matcher.group(1).replace(',', '.');
			String[] valores = valorStr.split("-");

			double valorFinal = valores.length > 1
					? Math.max(Double.parseDouble(valores[0].trim()), Double.parseDouble(valores[1].trim()))
					: Double.parseDouble(valores[0].trim());

			return valorFinal;
		}
		return 0;
	}

	private String calcularNutriScore(Map<String, Double> dados) {
		int pontosNegativos = calcularPontos(dados.getOrDefault("valorEnergetico", 0.0), nutrientRanges.get("energia"))
				+ calcularPontos(dados.getOrDefault("acucaresTotais", 0.0), nutrientRanges.get("acucar"))
				+ calcularPontos(dados.getOrDefault("gordurasSaturadas", 0.0), nutrientRanges.get("gordurasSaturadas"))
				+ calcularPontos(dados.getOrDefault("sodio", 0.0), nutrientRanges.get("sodio"));

		int pontosPositivos = calcularPontos(dados.getOrDefault("proteinas", 0.0), nutrientRanges.get("proteinas"))
				+ calcularPontos(dados.getOrDefault("fibrasAlimentares", 0.0), nutrientRanges.get("fibras"))
				+ calcularPontos(dados.getOrDefault("percentualFrutasLegumesOleaginosas", 0.0),
						nutrientRanges.get("frutasLegumesOleaginosas"));

		int pontuacaoFinal = pontosNegativos - pontosPositivos;

		return pontuacaoFinal <= -1 ? "A"
				: pontuacaoFinal <= 0 ? "B" : pontuacaoFinal <= 2 ? "C" : pontuacaoFinal <= 4 ? "D" : "E";
	}

	private int calcularPontos(double valor, double[][] ranges) {
		for (int i = 0; i < ranges.length; i++) {
			if (valor <= ranges[i][0]) {
				return i;
			}
		}
		return ranges.length;
	}
}