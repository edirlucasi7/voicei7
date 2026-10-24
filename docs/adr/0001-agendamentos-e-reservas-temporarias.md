# ADR 0001 — Agendamentos e reservas temporárias

Status: aceita. Data: 2026-10-24.

## Contexto

A interpretação de texto não consulta uma agenda. Precisamos reservar um horário
durante uma ligação, confirmar por decisão explícita do cliente e liberar quando
a ligação termina sem confirmação. Requisições concorrentes não podem ocupar o
mesmo horário, inclusive quando ainda não há registro para ele.

## Decisão

Usar PostgreSQL 17 em Docker, Flyway para migrations e Spring JDBC com SQL
explícito. Domínio e aplicação não dependem de Spring. Um adaptador transacional
coordena as portas usando READ COMMITTED.

Persistir somente horários escolhidos: agenda única, duração de 30 minutos,
inícios em :00 ou :30 no horário local de America/Fortaleza. Atividade e criação
usam instantes. Não cadastrar previamente slots disponíveis.

Uma linha singleton da agenda é bloqueada com SELECT FOR UPDATE antes de toda
alteração de ligação ou reserva. Ela protege também a ausência de agendamentos.
O lock termina no commit/rollback; nunca permanece aberto durante a ligação.
Um índice único parcial protege horários TEMPORARY ou CONFIRMED. Outro limita
cada ligação a uma reserva temporária. Ligações são ACTIVE, ENDED ou EXPIRED;
agendamentos são TEMPORARY, CONFIRMED, RELEASED ou EXPIRED.

A integração registra a ligação e envia heartbeat a cada 30 segundos. Cada
heartbeat válido renova sua validade por dois minutos. Não reativa ligações
encerradas ou expiradas. Encerramento libera temporárias, preservando confirmadas.
Uma tarefa a cada 30 segundos materializa expirações; leituras desconsideram
temporárias vencidas e escritas expiram antes de conferir ocupação. O instante
da decisão é capturado depois de adquirir o lock. Vencimento é inclusivo.

Confirmação exige nome, ligação ativa e reserva pertencente à ligação. Depois de
confirmado, o agendamento independe da atividade da ligação. Repetições de início,
reserva do mesmo horário, confirmação, encerramento e liberação são idempotentes,
sem reabrir estados terminais. Trocar de horário exige liberar a temporária antes.
Confirmação e encerramento concorrentes seguem a ordem de aquisição do lock.

Timeout de lock: cinco segundos, traduzido em HTTP 503. Conflitos de negócio e
unicidade retornam 409; entrada inválida 400; recurso inexistente 404. Novos erros
usam ProblemDetail. Nenhuma chamada de IA ocorre dentro dessas transações.

## Alternativas e consequências

Consultar e inserir sem lock permite corrida. Bloquear o agendamento inexistente
não protege a ausência. Lock otimista não atende à decisão de lock pessimista.
Slots previamente cadastrados exigem manter uma oferta de horários não necessária
nesta etapa. Manter a transação aberta durante a ligação esgota conexões.

O singleton serializa alterações de toda a agenda: simples e adequado à agenda
única, mas um limite de escala. Novas agendas podem ter linhas de lock próprias.
Heartbeat exige integração futura com telefonia. Não há autenticação/provedor de
telefonia nesta etapa; callId identifica a ligação, não é uma credencial.

## Validação

Testcontainers com PostgreSQL real valida migrations, índices, rollback, espera
por lock, timeout e concorrência. Relógio controlado valida expiração/renovação.
Testar uma única reserva para concorrentes, confirmação versus encerramento e
limpeza versus heartbeat. CI executa esses testes e os testes existentes sem IA.
