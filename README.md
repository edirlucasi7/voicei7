# Icety — agendamento por voz

A aplicação interpreta texto livre em `AppointmentRequest` e mantém uma agenda única no PostgreSQL. Durante a ligação, o horário escolhido fica reservado temporariamente; a confirmação explícita cria o compromisso definitivo. A integração com telefonia e a ligação automática entre parsing e reserva ficam para uma próxima etapa.

## Executar localmente

Requisitos: Java 21 e Docker com Compose. Execute os comandos na raiz do projeto.

```bash
cp .env.example .env
docker compose up -d --wait postgres ollama
docker compose exec ollama ollama pull qwen2.5:3b
./mvnw -Plocal-ai verify
```

O download do modelo tem aproximadamente 1,9 GB, além da imagem Docker. A primeira inferência carrega o modelo na memória e pode demorar; a execução usa CPU e não exige GPU. O volume Docker preserva o modelo entre reinicializações. Para parar o serviço:

```bash
docker compose stop ollama
```

Não é necessário ter uma conta OpenAI ou uma chave de API. O modelo executa localmente. Após baixar a imagem, o modelo e as dependências Maven, a extração não depende de um serviço externo.

## Testar pelo endpoint

Com o PostgreSQL e o Ollama iniciados e o modelo baixado, inicie o Spring. O Flyway aplica as migrations automaticamente:

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
├── domain/model            # Extração, ligação e agendamento: estado e regras de negócio
├── application/port/in     # Casos de uso de parsing e gestão da agenda
├── application/port/out    # Contratos de IA, persistência e transação
├── application/service    # Coordenação dos casos de uso
├── adapter/in/web          # Endpoints e erros HTTP
├── adapter/in/scheduling   # Limpeza periódica de ligações expiradas
├── adapter/out/ai          # OllamaAppointmentParser: prompt, esquema e integração Spring AI
├── adapter/out/persistence # Spring Data JPA e lock pessimista
└── configuration           # Conecta implementações e parâmetros
```

Fluxos: controller → caso de uso → porta de saída → Ollama ou PostgreSQL. O domínio e a aplicação usam apenas Java; dependências de Spring, HTTP e IA ficam nos adaptadores e na configuração.

## Configuração

O Spring importa o `.env` da raiz como um arquivo de propriedades. O `.env` fica fora do Git; `.env.example` documenta as variáveis. Variáveis exportadas no ambiente têm precedência.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Endereço do Ollama |
| `OLLAMA_MODEL` | `qwen2.5:3b` | Modelo previamente baixado no Ollama |
| `DB_PORT` | `5432` | Porta local publicada pelo Compose |
| `DB_URL` | `jdbc:postgresql://localhost:5432/icety` | Conexão com PostgreSQL |
| `DB_USERNAME` | `icety` | Usuário da aplicação e do Compose |
| `DB_PASSWORD` | `Obrigatoria, sem padrao` | Senha local da aplicação e do Compose |
| `RESERVATION_HEARTBEAT_INTERVAL` | `30s` | Frequência recomendada ao integrador, retornada no início da ligação |
| `RESERVATION_CALL_TIMEOUT` | `2m` | Validade da ligação desde o último heartbeat |
| `RESERVATION_CLEANUP_INTERVAL` | `30s` | Intervalo de limpeza das reservas vencidas |

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

Exige Docker ativo. Testcontainers sobe PostgreSQL 17 descartável, com credenciais e porta próprias, e aplica as migrations reais. Não usa nem apaga o banco do Compose. Executa também o teste de contexto e os testes do parser contra respostas HTTP simuladas com `MockRestServiceServer`, com a integração Spring AI real. Não requer Ollama ou chave. Verifica a requisição enviada, o contexto temporal, o esquema, a conversão do JSON, falhas de entrada/resposta e o endpoint com HTTP 200/400. Não avalia a interpretação linguística do modelo.

```bash
./mvnw -Plocal-ai verify
```

Executa também `AppointmentParsingIT` com o Ollama real. Requer o serviço iniciado e o modelo baixado. Falha se o modelo não estiver disponível, sem substituir a resposta por uma simulação. Usa as duas frases propostas, um pedido para amanhã às 15:00, um pedido sem data ou horário e um relógio fixo em segunda-feira, 19/10/2026, às 09:00 de Fortaleza. Verifica nome, data, horário, período e intenção. Os exemplos são avaliações pontuais; não garantem interpretação correta de toda entrada.

A suíte também testa o ciclo de vida das reservas, os contratos HTTP, as restrições SQL e a concorrência em conexões distintas. Os testes aguardam o bloqueio observado no PostgreSQL antes de liberar a primeira transação; incluem rollback, timeout e as disputas entre confirmação, encerramento, heartbeat e limpeza.

O GitHub Actions executa `./mvnw --batch-mode test` em pushes e pull requests, com Java 21, Docker e cache Maven. Os testes com IA real são exclusivamente locais.

Não há processamento de áudio, integração com provedor de telefonia, RAG ou divisão de documentos em chunks nesta etapa. RAG será relevante quando precisarmos recuperar informações externas para acrescentar ao contexto do modelo.


## Reservar durante uma ligação

A decisão está registrada na [ADR 0001](docs/adr/0001-agendamentos-e-reservas-temporarias.md).
Persistimos somente a data/hora escolhida pelo cliente, sem cadastrar slots previamente.
A agenda usa America/Fortaleza, com duração de 30 minutos e inícios em `:00` ou `:30`.
Os exemplos abaixo usam uma data futura; ajuste `appointment_time` quando necessário.
Somente estes endpoints de agenda podem ser usados sem o modelo Ollama baixado.

```bash
docker compose up -d --wait postgres
./mvnw spring-boot:run
```

Em outro terminal, registre uma ligação. `callId` deve ser um identificador único
fornecido pela futura integração de telefonia (até 128 caracteres):

```bash
call_id=ligacao-exemplo-001
appointment_time=2026-12-01T10:00:00
curl -i -X POST http://localhost:8080/api/calls \
  -H 'Content-Type: application/json' \
  -d "{\"callId\":\"$call_id\"}"
```

A resposta inclui `status`, `expiresAt` e `heartbeatIntervalSeconds`. A integração
deve enviar o heartbeat enquanto a ligação permanecer ativa, inclusive depois de
confirmar o agendamento:

```bash
curl -i -X POST "http://localhost:8080/api/calls/$call_id/heartbeat"
```

Repita a cada 30 segundos. A cada sinal, o prazo é renovado por dois minutos.
A aplicação fornece o endpoint; ela não simula sinais de uma ligação externa.
Heartbeat atrasado ou posterior ao encerramento retorna `409` e não reabre a ligação.

Consulte e reserve o horário (a consulta não garante exclusividade):

```bash
curl -sS --get http://localhost:8080/api/appointments/availability \
  --data-urlencode "dateTime=$appointment_time"

curl -i -X POST http://localhost:8080/api/appointments/reservations \
  -H 'Content-Type: application/json' \
  -d "{\"callId\":\"$call_id\",\"dateTime\":\"$appointment_time\"}"
```

A reserva retorna `201`, seu `id`, `status: TEMPORARY` e `expiresAt`. Ela impede
outra ligação de ocupar o horário. Copie o `id` retornado:

```bash
reservation_id=COLE_O_UUID_RETORNADO
curl -i -X POST "http://localhost:8080/api/appointments/$reservation_id/confirm" \
  -H 'Content-Type: application/json' \
  -d "{\"callId\":\"$call_id\",\"customerName\":\"Ana\"}"

curl -i -X POST "http://localhost:8080/api/calls/$call_id/end"
```

A confirmação retorna `CONFIRMED`, sem prazo de expiração. O encerramento preserva
o agendamento confirmado. Se não houve confirmação, encerra a ligação e libera o
horário imediatamente. A falta de sinais por dois minutos também libera a reserva;
a limpeza materializa `EXPIRED` a cada 30 segundos, e consultas/escritas já consideram
o vencimento antes dessa limpeza. No instante exato do vencimento a reserva já expirou.

Para trocar o horário, libere a reserva temporária e faça outra reserva:

```bash
curl -i -X POST "http://localhost:8080/api/appointments/$reservation_id/release" \
  -H 'Content-Type: application/json' \
  -d "{\"callId\":\"$call_id\"}"
```

Isso exige ligação ainda em andamento para uma nova reserva. Não há cancelamento
de agendamento confirmado nesta etapa. Cada ligação pode manter uma temporária.
Repetir início, reserva do mesmo horário, confirmação, liberação ou encerramento
não duplica registros nem reabre estados encerrados. Repetições de criação retornam
`200`; as demais transições retornam `200`.

Entradas inválidas retornam `400`, recursos inexistentes `404`, conflitos de estado,
propriedade ou horário `409` e timeout de lock `503`. Os novos erros usam
`application/problem+json` com `code` (`INVALID`, `NOT_FOUND`, `CONFLICT`, `BUSY`).

## Como o lock protege a reserva

O `ScheduleJpaRepository` usa `@Lock(PESSIMISTIC_WRITE)` para coordenar as
alterações da agenda em transações declarativas com `@Transactional`. A transação expira reservas vencidas,
verifica ocupação e persiste a mudança antes de liberar o lock. Uma segunda
requisição aguarda e consulta o estado atualizado. O banco também aplica índices
únicos para horário ocupado e temporária por ligação.

O lock permanece apenas durante essa transação, com espera limitada a cinco
segundos via configuração da consulta JPA. Durante a conversa, o estado persistido da reserva protege o horário.
Confirmação e encerramento concorrentes seguem a ordem de aquisição do lock.
A estratégia serializa alterações da agenda única; novas agendas exigirão rever
esse limite. Não há chamada ao Ollama dentro da transação.

Se a porta `5432` já estiver ocupada, configure `DB_PORT=5433` e
`DB_URL=jdbc:postgresql://localhost:5433/icety` no `.env` antes de iniciar.

O volume `postgres_data` mantém os dados após reiniciar o serviço. Para parar os
serviços, use `docker compose stop`; migrations são validadas pelo Flyway a cada
inicialização. Depois de criar o volume, alterar `DB_USERNAME`/`DB_PASSWORD` não
altera automaticamente as credenciais já existentes no PostgreSQL.
