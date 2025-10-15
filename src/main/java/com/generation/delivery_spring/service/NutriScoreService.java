package com.generation.delivery_spring.service;

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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generation.delivery_spring.exception.GeminiApiException;
import com.generation.delivery_spring.records.DadosNutricionais;
import com.generation.delivery_spring.records.RespostaGemini;
import com.generation.delivery_spring.records.ResultadoNutriScore;

import io.github.cdimascio.dotenv.Dotenv;

@Service
public class NutriScoreService {

    private static final Logger log = LoggerFactory.getLogger(NutriScoreService.class);
    private static final Dotenv dotenv = Dotenv.load();
    
    // Configurações da API Gemini
    private static final String GEMINI_ENDPOINT = dotenv.get("GEMINI_ENDPOINT");
    private static final String GEMINI_MODEL = dotenv.get("GEMINI_MODEL");
    private static final String API_KEY = dotenv.get("API_KEY");

    private final RestTemplate restTemplate;

    public NutriScoreService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Método principal: pesquisa o NutriScore de um produto.
     * 
     * Passos:
     * 1. Cria o prompt para a API
     * 2. Chama a API Gemini
     * 3. Extrai os dados nutricionais da resposta
     * 4. Calcula a classificação (A, B, C, D ou E)
     */
    public ResultadoNutriScore pesquisarNutriScore(String produto) {
        // Passo 1: Preparar a pergunta
        String prompt = criarPrompt(produto.trim());
        
        // Passo 2: Chamar a API e obter resposta
        RespostaGemini respostaAPI = chamarGeminiAPI(prompt);
        
        // Passo 3: Extrair dados nutricionais
        DadosNutricionais dadosNutricionais = extrairDadosNutricionais(respostaAPI.texto());
        
        // Passo 4: Calcular classificação
        String classificacao = calcularClassificacao(dadosNutricionais);

        return new ResultadoNutriScore(dadosNutricionais, classificacao);
    }

    /**
     * Cria a pergunta que será enviada para a API Gemini.
     */
    private String criarPrompt(String produto) {
        return "Forneça informações nutricionais médias por 100g do prato " + produto + 
               ". Inclua: Valor energético (kcal), Açúcares totais (g), Gorduras saturadas (g), " +
               "Sódio (mg), Proteínas (g), Fibras alimentares (g) e percentual de frutas, legumes e oleaginosas.";
    }

    /**
     * Chama a API Gemini e retorna a resposta encapsulada em um record.
     */
    private RespostaGemini chamarGeminiAPI(String prompt) {
        try {
            // Preparar os cabeçalhos da requisição
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Preparar o corpo da requisição
            Map<String, Object> requestBody = criarCorpoRequisicao(prompt);

            // Criar a requisição completa
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Montar a URL completa
            String url = String.format("%s/%s:generateContent?key=%s", 
                                     GEMINI_ENDPOINT, GEMINI_MODEL, API_KEY);

            log.info("Chamando API Gemini: {}", url);
            
            // Fazer a chamada HTTP POST
            String response = restTemplate.postForObject(url, request, String.class);
            
            // Extrair o texto da resposta JSON e retornar como record
            String textoResposta = extrairTextoResposta(response);
            return new RespostaGemini(textoResposta);

        } catch (Exception e) {
            log.error("Erro ao chamar a API Gemini", e);
            throw new GeminiApiException("Erro na comunicação com a API Gemini", 
                                        HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Cria o corpo da requisição no formato esperado pela API Gemini.
     */
    private Map<String, Object> criarCorpoRequisicao(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(textPart));
        Map<String, Object> contents = Map.of("contents", List.of(parts));
        
        return contents;
    }

    /**
     * Extrai o texto da resposta JSON da API Gemini.
     */
    private String extrairTextoResposta(String jsonResponse) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);
        
        return root.path("candidates")
                   .get(0)
                   .path("content")
                   .path("parts")
                   .get(0)
                   .path("text")
                   .asText();
    }

    /**
     * Extrai os valores nutricionais do texto retornado pela API.
     * 
     * Usa expressões regulares (regex) para encontrar os números no texto
     * e retorna um record DadosNutricionais tipado e seguro.
     */
    private DadosNutricionais extrairDadosNutricionais(String texto) {
        double valorEnergetico = buscarNumero(texto, "Valor energético.*?(\\d+(?:,\\d+)?)\\s*kcal");
        double acucaresTotais = buscarNumero(texto, "Açúcares totais.*?(\\d+(?:,\\d+)?)\\s*g");
        double gordurasSaturadas = buscarNumero(texto, "Gorduras saturadas.*?(\\d+(?:,\\d+)?)\\s*g");
        double sodio = buscarNumero(texto, "Sódio.*?(\\d+(?:,\\d+)?)\\s*mg");
        double proteinas = buscarNumero(texto, "Proteínas.*?(\\d+(?:,\\d+)?)\\s*g");
        double fibrasAlimentares = buscarNumero(texto, "Fibras alimentares.*?(\\d+(?:,\\d+)?)\\s*g");
        double percentualFrutasLegumesOleaginosas = buscarNumero(texto, "% de frutas.*?(\\d+(?:,\\d+)?)\\s*%");
        
        return new DadosNutricionais(
            valorEnergetico,
            acucaresTotais,
            gordurasSaturadas,
            sodio,
            proteinas,
            fibrasAlimentares,
            percentualFrutasLegumesOleaginosas
        );
    }

    /**
     * Busca um número no texto usando uma expressão regular.
     * 
     * Retorna 0.0 se não encontrar nada.
     */
    private double buscarNumero(String texto, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(texto);
        
        if (matcher.find()) {
            String numeroEncontrado = matcher.group(1);
            // Trocar vírgula por ponto para converter para double
            numeroEncontrado = numeroEncontrado.replace(",", ".");
            return Double.parseDouble(numeroEncontrado);
        }
        
        return 0.0;
    }

    /**
     * Calcula a classificação NutriScore (A, B, C, D ou E).
     * 
     * Fórmula:
     * - Pontos negativos: energia + açúcar + gordura + sódio (dividido por 100)
     * - Pontos positivos: proteína + fibras + frutas (dividido por 10)
     * - Pontuação final = negativos - positivos
     * 
     * Classificação:
     * - A: pontuação <= 0 (melhor)
     * - B: pontuação <= 2
     * - C: pontuação <= 4
     * - D: pontuação <= 6
     * - E: pontuação > 6 (pior)
     */
    private String calcularClassificacao(DadosNutricionais dados) {
        // Calcular pontos negativos (coisas ruins)
        int pontosNegativos = (int) ((dados.valorEnergetico() + 
                                      dados.acucaresTotais() + 
                                      dados.gordurasSaturadas() + 
                                      dados.sodio()) / 100);
        
        // Calcular pontos positivos (coisas boas)
        int pontosPositivos = (int) ((dados.proteinas() + 
                                      dados.fibrasAlimentares() + 
                                      dados.percentualFrutasLegumesOleaginosas()) / 10);

        // Pontuação final
        int pontuacaoFinal = pontosNegativos - pontosPositivos;

        // Determinar a classificação
        if (pontuacaoFinal <= 0) return "A";
        if (pontuacaoFinal <= 2) return "B";
        if (pontuacaoFinal <= 4) return "C";
        if (pontuacaoFinal <= 6) return "D";
        return "E";
    }
}