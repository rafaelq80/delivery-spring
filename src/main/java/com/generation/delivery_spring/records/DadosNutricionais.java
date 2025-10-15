package com.generation.delivery_spring.records;

public record DadosNutricionais(
		double valorEnergetico,
        double acucaresTotais,
        double gordurasSaturadas,
        double sodio,
        double proteinas,
        double fibrasAlimentares,
        double percentualFrutasLegumesOleaginosas
) {}
