package com.generation.delivery_spring.records;

public record ResultadoNutriScore(
    DadosNutricionais dados,
    String classificacao
) {}
