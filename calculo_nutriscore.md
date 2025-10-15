# NutriScoreService - Documentação

## O que faz?

O `NutriScoreService` analisa a qualidade nutricional de um prato usando Inteligência Artificial. Ele busca informações nutricionais e retorna uma classificação de A a E.

## Como funciona?

Quando você pesquisa o NutriScore de um produto:

1. Prepara uma pergunta detalhada sobre o alimento
2. Envia para a API Gemini (IA do Google)
3. Extrai os dados nutricionais da resposta
4. Calcula a classificação (A = mais saudável, E = menos saudável)

## Método Principal

### `pesquisarNutriScore(String produto)`

Recebe o nome de um prato e retorna um objeto `ResultadoNutriScore` contendo:

- **Dados nutricionais:** valor energético, açúcares, gorduras, sódio, proteínas, fibras e percentual de frutas/legumes
- **Classificação:** letra de A a E indicando a qualidade nutricional

**Exemplo de uso:**

```java
@Autowired
private NutriScoreService nutriScoreService;

ResultadoNutriScore resultado = nutriScoreService.pesquisarNutriScore("Pizza Margherita");

System.out.println("Classificação: " + resultado.classificacao()); // Ex: "C"
System.out.println("Valor energético: " + resultado.dados().valorEnergetico() + " kcal");
System.out.println("Açúcares: " + resultado.dados().acucaresTotais() + "g");
System.out.println("Proteínas: " + resultado.dados().proteinas() + "g");
```

## Classificação NutriScore

| Letra | Significado                        | Pontuação |
| ----- | ---------------------------------- | --------- |
| A     | Excelente - Muito saudável         | ≤ 0       |
| B     | Bom - Saudável                     | ≤ 2       |
| C     | Aceitável - Moderadamente saudável | ≤ 4       |
| D     | Fraco - Pouco saudável             | ≤ 6       |
| E     | Péssimo - Não recomendado          | > 6       |

## Como a Pontuação é Calculada

A classificação leva em conta dois grupos de fatores:

### Fatores Negativos (Aumentam a pontuação)

- Valor energético (kcal)
- Açúcares totais (g)
- Gorduras saturadas (g)
- Sódio (mg)

### Fatores Positivos (Diminuem a pontuação)

- Proteínas (g)
- Fibras alimentares (g)
- Percentual de frutas, legumes e oleaginosas

**Fórmula:** `Pontuação = (Negativos ÷ 100) - (Positivos ÷ 10)`

## Dados Nutricionais Retornados

Todos os valores são por **100g** do prato:

- **Valor energético:** em kcal
- **Açúcares totais:** em gramas
- **Gorduras saturadas:** em gramas
- **Sódio:** em miligramas
- **Proteínas:** em gramas
- **Fibras alimentares:** em gramas
- **Frutas, legumes e oleaginosas:** em percentual (%)

## Configuração Necessária

Adicione no arquivo `.env` na raiz do projeto:

```
GEMINI_ENDPOINT=https://generativelanguage.googleapis.com/v1beta/models
GEMINI_MODEL=gemini-pro
API_KEY=sua_chave_da_gemini_aqui
```

Obtenha uma chave gratuita em: https://ai.google.dev/

## Dependências Adicionais

```xml
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
</dependency>
```

## Tratamento de Erros

O serviço lança uma exceção `GeminiApiException` quando:

- A API Gemini não conseguir processar a requisição
- Houver erro na comunicação com a IA
- A resposta da API estiver em formato inesperado

## Observações Importantes

- O serviço usa **expressões regulares** para extrair números do texto da IA
- Se um valor não for encontrado na resposta, ele é considerado como **0.0**
- A resposta da IA é em português para melhor precisão
- O cálculo considera valores médios por 100g do alimento

## Classes Record Utilizadas

### `ResultadoNutriScore`

Retorna o resultado completo da análise nutricional.

**Campos:**

- `dados()` → `DadosNutricionais` - todos os valores nutricionais
- `classificacao()` → `String` - a letra da classificação (A, B, C, D ou E)

```java
ResultadoNutriScore resultado = nutriScoreService.pesquisarNutriScore("Pizza");
String classe = resultado.classificacao(); // "C"
DadosNutricionais dados = resultado.dados();
```

### `DadosNutricionais`

Contém todos os valores nutricionais extraídos pela IA.

**Campos (todos em Double):**

- `valorEnergetico()` → valor em kcal
- `acucaresTotais()` → valor em gramas
- `gordurasSaturadas()` → valor em gramas
- `sodio()` → valor em miligramas
- `proteinas()` → valor em gramas
- `fibrasAlimentares()` → valor em gramas
- `percentualFrutasLegumesOleaginosas()` → valor em percentual

```java
DadosNutricionais dados = resultado.dados();
System.out.println("Proteína: " + dados.proteinas() + "g");
System.out.println("Fibra: " + dados.fibrasAlimentares() + "g");
```

### `RespostaGemini`

Encapsula a resposta em texto recebida da API Gemini.

**Campo:**

- `texto()` → `String` - o texto completo retornado pela IA

*Nota: Este record é usado internamente pelo serviço, você não precisa trabalhar diretamente com ele.*

## Exemplo de Resposta Completa

```java
ResultadoNutriScore resultado = nutriScoreService.pesquisarNutriScore("Salada de Frango");

System.out.println("Classificação: " + resultado.classificacao()); // "A"

DadosNutricionais dados = resultado.dados();
System.out.println("Valor energético: " + dados.valorEnergetico()); // 165.0
System.out.println("Açúcares: " + dados.acucaresTotais()); // 2.5
System.out.println("Gordura saturada: " + dados.gordurasSaturadas()); // 1.8
System.out.println("Sódio: " + dados.sodio()); // 450.0
System.out.println("Proteína: " + dados.proteinas()); // 28.0
System.out.println("Fibras: " + dados.fibrasAlimentares()); // 3.5
System.out.println("Frutas/legumes: " + dados.percentualFrutasLegumesOleaginosas()); // 40.0
```