# Icety — agendamento por voz

Primeira etapa: transformar texto livre em `AppointmentRequest`, um record Java com nome, data, horário, período e intenção. A entrada pode futuramente vir de uma transcrição de áudio. A aplicação oferece um endpoint para experimentar a extração, além dos testes.

## Executar localmente

Requisitos: Java 21 e Docker com Compose. Execute os comandos na raiz do projeto.

```bash
cp .env.example .env
docker compose up -d ollama
docker compose exec ollama ollama pull qwen2.5:3b
./mvnw -Plocal-ai verify
```

O download do modelo tem aproximadamente 1,9 GB, além da imagem Docker. A primeira inferência carrega o modelo na memória e pode demorar; a execução usa CPU e não exige GPU. O volume Docker preserva o modelo entre reinicializações. Para parar o serviço:

```bash
docker compose stop ollama
```

Não é necessário ter uma conta OpenAI ou uma chave de API. O modelo executa localmente. Após baixar a imagem, o modelo e as dependências Maven, a extração não depende de um serviço externo.

## Testar pelo endpoint

Com o Ollama iniciado e o modelo baixado, inicie o Spring:

```bash
./mvnw spring-boot:run
```

Em outro terminal, envie o texto do usuário:

```bash
curl -X POST http://localhost:8080/api/appointments/parse \
  -H 'Content-Type: application/json' \
  -d '{"text":"Tem vaga para amanhã às 10 horas?"}'
```

A resposta HTTP 200 contém `customerName`, `requestedDate`, `specificTime`, `period` e `intent`. O endpoint usa a data atual em Fortaleza; os testes usam uma data fixa. Texto ausente, nulo ou em branco e JSON inválido retornam HTTP 400. A extração não agenda nem confirma consultas. Erros do Ollama ou de conversão resultam em erro da requisição.

O código, os campos JSON e os enums estão em inglês. Os prompts, incluindo as descrições do esquema, continuam em português. Períodos: `MORNING`, `AFTERNOON`, `EVENING`, `UNSPECIFIED`. Intenções: `BOOK`, `CHECK_AVAILABILITY`, `CANCEL`, `OTHER`.

## Estrutura hexagonal

```text
tech.voice.icety
├── domain/model            # AppointmentRequest: dados independentes de frameworks
├── application/port/in     # ParseAppointmentUseCase: entrada da aplicação
├── application/port/out    # AppointmentParser: contrato de extração
├── application/service    # ParseAppointmentService: valida e coordena o caso de uso
├── adapter/in/web          # AppointmentController: recebe HTTP e chama o caso de uso
├── adapter/out/ai          # OllamaAppointmentParser: prompt, esquema e integração Spring AI
└── configuration           # AppointmentConfiguration: conecta as implementações
```

Fluxo: controller → caso de uso → porta de saída → Ollama. O domínio e a aplicação usam apenas Java; dependências de Spring, HTTP e IA ficam nos adaptadores e na configuração.

## Configuração

O Spring importa o `.env` da raiz como um arquivo de propriedades. O `.env` fica fora do Git; `.env.example` documenta as variáveis. Variáveis exportadas no ambiente têm precedência.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Endereço do Ollama |
| `OLLAMA_MODEL` | `qwen2.5:3b` | Modelo previamente baixado no Ollama |

A temperatura é `0.0`, para reduzir a variação das respostas. Isso não garante que o modelo sempre interprete corretamente uma frase. Não há download automático do modelo ao iniciar o Spring.

## Como funciona

1. **Prompt:** instruções para extrair os campos, interpretar intenções e deixar informações ausentes como `null` ou `UNSPECIFIED`.
2. **Contexto temporal:** o serviço acrescenta a data e a hora atuais em `America/Fortaleza`. O prompt inclui também as datas já calculadas de hoje e amanhã, além de exemplos de extração. O modelo usa essas referências para interpretar datas relativas e recebe uma orientação geral para qualquer dia da semana, sem cálculos específicos por dia. Um `Clock` permite fixar essa referência nos testes.
3. **JSON Schema:** `BeanOutputConverter` gera um esquema a partir do record `ExtractedAppointment` do adaptador e de suas descrições em português. As anotações que permitem valores `null` ficam nesse adaptador, mantendo o domínio independente de bibliotecas. O serviço envia esse esquema ao Ollama para orientar a estrutura da resposta.
4. **Objeto tipado:** o Spring AI converte o JSON em `ExtractedAppointment`, incluindo `LocalDate` e enums. O adaptador transforma esse resultado em `AppointmentRequest`, o modelo do domínio. Quando há um horário específico, o enum independente `DayPeriod` calcula o período com `DayPeriod.from(LocalTime)`: 06:00–11:59 é `MORNING`, 12:00–17:59 é `AFTERNOON`, e os demais horários são `EVENING`. Isso corrige contradições do modelo como `10:00` com `AFTERNOON`. Sem horário específico, preserva o período extraído. A interpretação do texto é responsabilidade do modelo; se ele omitir o horário, `specificTime` permanece `null`, sem extração manual complementar. Texto vazio é rejeitado antes da chamada; erros da integração ou da conversão são propagados.

Exemplo para “Tem vaga para amanhã às 10 horas?”, considerando a referência de teste de 19/10/2026:

```json
{
  "customerName": null,
  "requestedDate": "2026-10-20",
  "specificTime": "10:00",
  "period": "MORNING",
  "intent": "CHECK_AVAILABILITY"
}
```

“Primeiro horário da tarde” indica `AFTERNOON` e horário específico `null`: ainda não existe uma agenda que informe qual seria esse horário. Extrair intenção não significa confirmar uma consulta ou consultar vagas reais.

## Testes

```bash
./mvnw test
```

Executa o teste de contexto e os testes do parser contra respostas HTTP simuladas com `MockRestServiceServer`, com a integração Spring AI real. Não requer Docker, Ollama ou chave. Verifica a requisição enviada, o contexto temporal, o esquema, a conversão do JSON, falhas de entrada/resposta e o endpoint com HTTP 200/400. Não avalia a interpretação linguística do modelo.

```bash
./mvnw -Plocal-ai verify
```

Executa também `AppointmentParsingIT` com o Ollama real. Requer o serviço iniciado e o modelo baixado. Falha se o modelo não estiver disponível, sem substituir a resposta por uma simulação. Usa as duas frases propostas, um pedido para amanhã às 15:00, um pedido sem data ou horário e um relógio fixo em segunda-feira, 19/10/2026, às 09:00 de Fortaleza. Verifica nome, data, horário, período e intenção. Os exemplos são avaliações pontuais; não garantem interpretação correta de toda entrada.

O GitHub Actions executa `./mvnw --batch-mode test` em pushes e pull requests, com Java 21 e cache Maven. Os testes com IA real são exclusivamente locais.

Não há banco, Testcontainers, processamento de áudio, RAG ou divisão de documentos em chunks nesta etapa. RAG será relevante quando precisarmos recuperar informações externas para acrescentar ao contexto do modelo.
