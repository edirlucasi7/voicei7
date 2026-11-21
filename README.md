# Icety — agendamento por voz

A aplicação interpreta texto livre em `AppointmentRequest` e mantém uma agenda única no PostgreSQL. Durante a ligação, o horário escolhido fica reservado temporariamente; a confirmação explícita cria o compromisso definitivo. O atendimento por texto combina RAG e tools para operar a agenda durante uma ligação. A integração com telefonia fica para uma próxima etapa.

## Executar localmente

Requisitos: Java 21 e Docker com Compose. Execute os comandos na raiz do projeto.

```bash
cp .env.example .env
# Preencha DB_PASSWORD no .env com uma senha aleatória antes de continuar.
docker compose up -d --wait postgres ollama
docker compose exec ollama ollama pull qwen2.5:7b
docker compose exec ollama ollama pull nomic-embed-text
./mvnw -Plocal-ai verify
```

O modelo de conversa tem aproximadamente 4,7 GB e o de embeddings aproximadamente 274 MB, além das imagens Docker. A primeira inferência carrega o modelo na memória e pode demorar; a execução usa CPU e não exige GPU. O volume Docker preserva o modelo entre reinicializações. Para parar o serviço:

```bash
docker compose stop ollama
```

O atendimento e a simulação de voz executam localmente, sem chave ou cobrança de API. Após baixar imagens, modelos e dependências Maven, o processamento não depende de um serviço externo.

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

Defina uma senha aleatória em `DB_PASSWORD` no `.env` antes de iniciar os serviços. Para gerar uma senha, use `openssl rand -hex 32` e copie a saída apenas para o arquivo local. Nunca versione esse valor. O Compose recusa iniciar com a senha ausente ou vazia.

O Spring importa o `.env` da raiz como um arquivo de propriedades. O `.env` fica fora do Git; `.env.example` documenta as variáveis. Variáveis exportadas no ambiente têm precedência.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Endereço do Ollama |
| `OLLAMA_MODEL` | `qwen2.5:7b` | Modelo previamente baixado no Ollama |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Modelo local de embeddings com 768 dimensões |
| `KNOWLEDGE_DIRECTORY` | `knowledge` | Pasta de arquivos TXT/Markdown UTF-8 |
| `KNOWLEDGE_INGEST_ON_STARTUP` | `true` | Atualiza o índice documental ao iniciar a aplicação |
| `SCHEDULE_CLOSED_DAYS` | `SUNDAY` | Dias fechados, separados por vírgula, com nomes de `DayOfWeek` em inglês |
| `KNOWLEDGE_TOP_K` | `4` | Máximo de trechos recuperados por pergunta |
| `KNOWLEDGE_SIMILARITY_THRESHOLD` | `0.5` | Similaridade mínima, de 0 a 1 |
| `DB_PORT` | `5432` | Porta local publicada pelo Compose |
| `DB_URL` | `jdbc:postgresql://localhost:5432/icety` | Conexão com PostgreSQL |
| `DB_USERNAME` | `icety` | Usuário da aplicação e do Compose |
| `DB_PASSWORD` | Obrigatória, sem padrão | Senha definida no `.env` local ou no ambiente |
| `RESERVATION_HEARTBEAT_INTERVAL` | `30s` | Frequência recomendada ao integrador, retornada no início da ligação |
| `RESERVATION_CALL_TIMEOUT` | `2m` | Validade da ligação desde o último heartbeat |
| `RESERVATION_CLEANUP_INTERVAL` | `30s` | Intervalo de limpeza das reservas vencidas |

A temperatura é `0.0`, para reduzir a variação das respostas. Isso não garante que o modelo sempre interprete corretamente uma frase. Não há download automático do modelo ao iniciar o Spring.

### Dias fechados: regra obrigatória da agenda

Por padrão, a agenda não permite reservas aos domingos, no fuso `America/Fortaleza`.
A configuração `SCHEDULE_CLOSED_DAYS` é a fonte dessa regra e também é informada à IA.
Por exemplo, `SCHEDULE_CLOSED_DAYS=SATURDAY,SUNDAY` fecha os dois dias; uma lista vazia
não fecha nenhum dia. Valores desconhecidos impedem a inicialização. Reinicie a aplicação
após alterar a configuração.

A consulta de disponibilidade retorna `{"available":false}` em dias fechados. Tentativas
de reservar ou confirmar uma temporária nesses dias retornam `409` com código `CONFLICT`.
As tools passam pela mesma validação: pedidos para ignorar a regra e informações
contraditórias nos documentos não autorizam a operação. O texto da resposta depende do
modelo, mas o bloqueio de persistência é feito pela agenda. Agendamentos já confirmados
são preservados, inclusive na repetição da confirmação. Reservas temporárias antigas
em dias fechados podem ser liberadas, mas não confirmadas.

Não há faixa de abertura/fechamento nos demais dias nesta etapa. Políticas descritas
somente em Markdown, como taxas e prazos, orientam respostas; para bloquear operações,
precisam de validação estruturada no domínio, como a regra de dias fechados.

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

Exige Docker ativo. Testcontainers sobe PostgreSQL 17 com pgvector descartável, com credenciais e porta próprias, e aplica as migrations reais. Não usa nem apaga o banco do Compose. Executa também o teste de contexto e os testes do parser contra respostas HTTP simuladas com `MockRestServiceServer`, com a integração Spring AI real. Não requer Ollama ou chave. Verifica a requisição enviada, o contexto temporal, o esquema, a conversão do JSON, falhas de entrada/resposta e o endpoint com HTTP 200/400. Não avalia a interpretação linguística do modelo.

```bash
./mvnw -Plocal-ai verify
```

Executa também `AppointmentParsingIT` com o Ollama real. Requer o serviço iniciado e o modelo baixado. Falha se o modelo não estiver disponível, sem substituir a resposta por uma simulação. Usa as duas frases propostas, um pedido para amanhã às 15:00, um pedido sem data ou horário e um relógio fixo em segunda-feira, 19/10/2026, às 09:00 de Fortaleza. Verifica nome, data, horário, período e intenção. Os exemplos são avaliações pontuais; não garantem interpretação correta de toda entrada.

A suíte também testa o ciclo de vida das reservas, os contratos HTTP, as restrições SQL e a concorrência em conexões distintas. Os testes aguardam o bloqueio observado no PostgreSQL antes de liberar a primeira transação; incluem rollback, timeout e as disputas entre confirmação, encerramento, heartbeat e limpeza.

O GitHub Actions executa `./mvnw --batch-mode test` em pushes e pull requests, com Java 21, Docker e cache Maven. Os testes com IA real são exclusivamente locais.

Não há processamento de áudio nem integração com provedor de telefonia nesta etapa.


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

## RAG: documentos e embeddings locais

O RAG busca informações nos arquivos `.txt` e `.md` da pasta `knowledge/`, incluindo subpastas.
Cada arquivo é dividido em trechos de até aproximadamente 500 tokens. O Ollama gera embeddings
numéricos de 768 dimensões e o pgvector armazena texto, origem, posição e embedding.
Perguntas usam o mesmo modelo para recuperar até quatro trechos relacionados.
A agenda real é consultada pelas tools, nunca inferida dos documentos.

O arquivo `knowledge/example.md` é **fictício**. Substitua-o por FAQ, serviços, endereço e orientações reais.
Documentos não alteram regras de horário, duração ou reserva implementadas no domínio.
Em caso de divergência sobre dias fechados, prevalece `SCHEDULE_CLOSED_DAYS`.
Não há upload ou leitura de PDF nesta etapa.

Ao iniciar a aplicação, os arquivos são ingeridos automaticamente, após as migrations.
Os modelos precisam estar previamente baixados. Após editar os documentos, reinicie a
aplicação ou execute a ingestão explicitamente:

```bash
docker compose up -d --wait postgres ollama
docker compose exec ollama ollama pull nomic-embed-text
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest
```

O comando aplica migrations, ingere os arquivos, informa quantos foram atualizados, preservados
ou excluídos e encerra sem abrir o servidor HTTP. Na inicialização normal, o mesmo resumo
é registrado e a aplicação continua atendendo. Se a ingestão falhar, a inicialização falha;
uma atualização malsucedida preserva o índice anterior. Arquivos inalterados não chamam o
modelo de embeddings. A busca e a ingestão de conteúdo novo exigem o modelo previamente baixado.

Para desativar somente a ingestão automática, defina `KNOWLEDGE_INGEST_ON_STARTUP=false`.
O perfil `ingest` continua executando a ingestão explícita mesmo com essa opção desativada.

Hashes evitam duplicação e reprocessamento de arquivos inalterados. Arquivos alterados substituem
seus trechos; arquivos removidos saem do índice após uma leitura completa da pasta.
Pasta ausente ou ilegível faz o comando falhar. Uma pasta válida vazia remove todo o corpus.
A atualização é transacional: falhas de embeddings preservam o índice anterior.
Um lock exclusivo do catálogo serializa ingestões, sem bloquear a agenda; consultas continuam
vendo o índice anterior até o commit. Execute um comando de ingestão por vez para indexar
um retrato coerente dos arquivos, sem editá-los durante a execução.

Para forçar reindexação de arquivos inalterados:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest \
  -Dspring-boot.run.arguments=--knowledge.reindex=true
```

Ingestão e consultas devem usar o mesmo modelo. Ao mudar para outro modelo de 768 dimensões,
pare o atendimento, reindexe todo o corpus e reinicie com a mesma configuração.
Modelos de outras dimensões exigem uma nova migration e reconstrução dos embeddings.

O Compose agora usa `pgvector/pgvector:pg17`, mantendo PostgreSQL 17 e o volume existente.
Recrie o serviço com `docker compose up -d --wait postgres` antes de iniciar a aplicação;
não remova o volume. A migration V3 instala a extensão e cria o schema vetorial pelo Flyway.
Um PostgreSQL externo também precisa disponibilizar a extensão `vector` e permitir sua criação.

## Atendimento com ChatClient e tools

Inicie o servidor normalmente; a ingestão automática atualiza os documentos:

```bash
./mvnw spring-boot:run
```

Use `/api/calls` para iniciar a ligação e mantenha o heartbeat nos endpoints existentes.
Envie uma mensagem por vez para a mesma ligação:

```bash
curl -X POST http://localhost:8080/api/calls/ligacao-exemplo-001/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"Qual é o endereço?"}'

curl -X POST http://localhost:8080/api/calls/ligacao-exemplo-001/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"Pode consultar e reservar amanhã às 10h, se estiver livre?"}'

curl -X POST http://localhost:8080/api/calls/ligacao-exemplo-001/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"Sim, confirmo esse horário. Meu nome é Ana."}'
```

Para testar liberação, após criar uma reserva temporária e antes de confirmá-la, envie
`{"text":"Quero cancelar essa reserva temporária."}` ao mesmo endpoint. A tool
`releaseReservation` libera o horário; agendamentos confirmados não podem ser cancelados.

Para verificar a proteção de dias fechados, inicie uma nova ligação e peça para reservar
uma data futura que caia no domingo, inclusive pedindo para ignorar as regras. A agenda
deve continuar sem reserva. A mesma tentativa diretamente em
`POST /api/appointments/reservations` deve retornar `409`; a consulta de disponibilidade
para essa data deve retornar `false`.

A resposta é `{"reply":"..."}`. O ChatClient recebe contexto documental e tools para consultar,
reservar, liberar e confirmar. O servidor fornece o `callId` pelo `ToolContext`; esse identificador
não faz parte dos argumentos escolhidos pela LLM. As tools delegam aos casos de uso existentes,
preservando transações, propriedade, expiração e concorrência. Não há transação de agenda
aberta durante inferência. Confirmação depende da interpretação da fala pela LLM; o prompt
exige confirmação explícita e a tool rejeita `explicitlyConfirmed=false`.

Reserva, confirmação e liberação retornam diretamente ao atendimento: a mensagem em
português é formada a partir do resultado real do caso de uso, sem uma segunda geração
da LLM para anunciar sucesso. Reserva temporária é apresentada como temporária, com
pedido de nome e confirmação, sem inventar um prazo de expiração. Uma confirmação só
pode atuar sobre a reserva que já existia no início da mensagem; não é permitido criar
e confirmar automaticamente no mesmo turno.

Se o modelo apenas anuncia uma ação em texto sem executar uma tool, uma interpretação
estruturada identifica o pedido e o encaminha às mesmas operações protegidas. Essa
interpretação não executa instruções dos documentos nem contorna as regras de domínio.

A cada mensagem, o servidor consulta a reserva temporária atual no banco e fornece seu
identificador internamente. A LLM pode omitir `reservationId` ao confirmar ou liberar;
se informar um ID, a propriedade continua sendo validada. Enquanto houver reserva
pendente, uma resposta livre sem operação verificável reapresenta o estado real da
reserva em vez de tratar o texto do modelo como confirmação. Falhas de geração após
uma operação concluída não apagam seu resultado nem provocam repetição automática.

O histórico fica em memória por ligação, limitado a 20 mensagens, incluindo resultados de tools,
para preservar o contexto da conversa entre turnos. O ID da reserva também é recuperado
do banco, independentemente desse histórico. Mensagens da mesma ligação são
serializadas. A limpeza periódica remove históricos de ligações encerradas ou expiradas.
Reiniciar perde o histórico, mas preserva os documentos indexados e a agenda.
O endpoint não inicia nem renova ligações. Retorna `400` para texto inválido, `404` para ligação
inexistente e `409` para ligação encerrada ou expirada. Falhas de infraestrutura não são
convertidas em uma resposta de sucesso; se uma tool concluir antes de uma falha do modelo,
a alteração já estará persistida e pode ser conferida pelos endpoints de agenda.

O parser `/api/appointments/parse` continua independente, com saída estruturada e sem tools ou RAG.

`./mvnw test` verifica embeddings e respostas HTTP simulados com Spring AI real e pgvector
em Testcontainers: ingestão idempotente, alteração/exclusão, rollback, busca e conversa com tools.
Os vetores simulados validam a integração, sem avaliar qualidade semântica.
`./mvnw -Plocal-ai verify` também executa `KnowledgeAttendanceIT`, que avalia busca em português,
resposta documental e reserva/confirmação com Ollama real em banco descartável.
Essas avaliações podem falhar se o modelo interpretar incorretamente uma mensagem; não há
resposta simulada substituindo o modelo no perfil local.

Na avaliação local com `qwen2.5:3b`, o cenário de insistência para reservar no domingo
foi recusado sem criar reserva. A busca vetorial em português funcionou, mas a resposta
documental veio vazia e o cenário de reserva/confirmação falhou: o modelo anunciou que
reservaria sem executar a reserva. Em avaliações anteriores, o perfil também registrou
falhas de extração nos testes do parser. O modelo permanece configurável por `OLLAMA_MODEL`;
o fluxo completo com esse modelo ainda não está aprovado pela avaliação real, mesmo com
os testes determinísticos passando.

## Simular uma chamada por voz no navegador

O fluxo inteiro roda na sua máquina, sem chave ou cobrança de API:

**Microfone → faster-whisper → Ollama com RAG/tools → Piper → alto-falante.**

Ollama gera as respostas e executa as tools pelos casos de uso existentes. O modelo de
conversa atual recebe texto; a transcrição é feita pelo faster-whisper e a síntese pelo
Piper, em um serviço Docker local de áudio. As validações da agenda continuam no Java.

No `.env`, habilite a voz:

```properties
VOICE_ENABLED=true
VOICE_TRANSCRIPTION_URL=http://localhost:8090/transcribe
VOICE_SYNTHESIS_URL=http://localhost:8090/synthesize
VOICE_TIMEOUT=60s
WHISPER_MODEL=small
PIPER_VOICE=pt_BR-faber-medium
```

Nenhuma variável `OPENAI_*` é utilizada. As URLs acima são para o Spring executando na
máquina; se executá-lo em outro container, use o hostname `audio` em vez de `localhost`.

Suba a infraestrutura, incluindo o perfil de voz:

```bash
docker compose --profile voice up -d --build --wait postgres ollama audio
./mvnw spring-boot:run
```

Na primeira inicialização, `audio` baixa o modelo Whisper `small` e a voz brasileira
`pt_BR-faber-medium`. O volume `audio_models` preserva os arquivos. O serviço só fica
saudável depois de carregar os dois modelos. Ele usa CPU e não exige GPU. Reserve
alguns minutos e espaço em disco para o primeiro build/download. Depois disso, os
modelos são carregados dos arquivos locais; as gravações não são enviadas à nuvem.
`WHISPER_MODEL` aceita `tiny`, `base`, `small` ou `medium`; modelos menores podem reduzir
a latência e a precisão. Após alterar modelo ou voz, recrie o serviço de áudio.

Confira a prontidão:

```bash
docker compose --profile voice ps
curl http://localhost:8090/health
curl http://localhost:8080/api/voice/status
```

Abra `http://localhost:8080/voice.html` no Chrome/Chromium usando localhost ou HTTPS e
permita o microfone:

1. Clique em **Ligar**. A página cria uma ligação e envia heartbeat automaticamente.
2. Clique em **Gravar**, fale e clique em **Enviar fala**. Limite: 30 segundos.
3. Confira a transcrição e a resposta na tela e ouça o áudio.
4. Para interromper, clique novamente em **Gravar**. O áudio anterior para e blocos
   atrasados são descartados. Grave a nova fala e envie normalmente.
5. Clique em **Desligar** para liberar microfone e conexões e encerrar a ligação.

A interrupção é manual nesta etapa. Detecção automática por fala/silêncio e telefonia
ficam para uma evolução. Uma nova fala pode aguardar enquanto o atendimento anterior
termina; existe no máximo uma fala pendente. Interromper o áudio não cancela uma tool
em execução nem desfaz uma operação concluída. O contexto seguinte informa quando a
resposta anterior pode não ter sido ouvida integralmente.

Ao desligar ou perder a conexão, o encerramento libera reservas temporárias e preserva
agendamentos confirmados. Se o encerramento não chegar ao servidor, a expiração por
falta de heartbeat continua protegendo a agenda. Sessões expiradas têm o WebSocket
fechado na verificação periódica. Cada turno tem supervisão de dois minutos; conexões
que excedem esse limite são encerradas sem interromper à força uma transação em curso.
As chamadas de áudio têm timeout configurável por `VOICE_TIMEOUT`.

### Contrato de áudio

- `GET /api/voice/status`: habilitação, sample rate e limite de gravação, sem segredos.
- `WS /api/calls/{callId}/voice`: exige ligação ativa, mesma origem no navegador e uma
  única conexão de voz por ligação. As rotas HTTP de texto continuam disponíveis.
- `input.start` inclui UUID `turnId`, `format: "pcm16"`, `sampleRate: 24000`, `channels: 1`
  e `playbackInterrupted` quando havia áudio ainda sendo reproduzido. Seguem frames
  binários PCM16 little-endian de até 9600 bytes e `input.commit` para o mesmo turno.
- O servidor monta WAV em memória e envia ao serviço local. Retorna `ready`,
  `input.started`, `state`, `transcript`, `reply`, `audio.delta`, `error` e `turn.done`.
  Eventos de turno incluem `turnId`; `audio.delta.audio` contém PCM16 mono 24 kHz em
  base64. IDs repetidos e sequências inválidas são recusados.
- Estados: `queued`, `transcribing`, `processing`, `speaking`. As falhas distinguem
  `TRANSCRIPTION_FAILED`, `ATTENDANCE_FAILED`, `SYNTHESIS_FAILED` e erros da ligação.
- O serviço `audio` publica apenas em `127.0.0.1:8090`, expõe `/health`, recebe WAV
  multipart em `POST /transcribe` e `{ "text": "..." }` em `POST /synthesize`. A síntese
  retorna streaming HTTP de PCM16 mono 24 kHz, com conversão da frequência da voz Piper.
  Texto vazio, WAV inválido e gravações acima de 30 segundos são rejeitados. Gravações
  não são salvas; buffers de entrada e saída são limitados.

Se a síntese falhar, o texto permanece visível. Se uma tool já tiver concluído antes de
uma falha, a agenda já pode ter sido alterada; não há repetição automática do turno.
A instabilidade do `qwen2.5:3b` descrita acima também pode aparecer no atendimento por voz.

As integrações usam as APIs dos projetos [faster-whisper](https://github.com/SYSTRAN/faster-whisper)
e [Piper](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/API_PYTHON.md). Os modelos
ficam no volume local, incluindo o arquivo de configuração da voz baixada.

`./mvnw test` verifica o fluxo com provedores simulados, WebSocket real local, validações,
fila, interrupção, origem e bloqueio de domingos. Não baixa modelos de áudio nem exige o
serviço `audio`. Os contratos do serviço Python podem ser testados no container:

```bash
docker compose --profile voice exec audio python -m unittest -v
```

O teste completo de fala e qualidade da voz deve ser feito na página com os modelos reais.

### Avaliação do fluxo de reserva em dois turnos

O atendimento usa `qwen2.5:7b` por padrão. No cenário real “Quero marcar uma consulta
para as quatro horas da tarde de hoje”, seguido de “Meu nome é Lucas e eu quero
confirmar o agendamento”, foi verificada a reserva às 16h, ainda temporária no primeiro
turno, e a confirmação com o nome Lucas no segundo. O modelo 3b interpretou 16h como 14h
nessa avaliação. A execução deve ser verificada em novos cenários; uma avaliação bem-sucedida
não garante interpretação perfeita de qualquer fala. O 7b usa mais memória e pode aumentar
a latência em CPU, especialmente quando a interpretação estruturada adicional é necessária.
