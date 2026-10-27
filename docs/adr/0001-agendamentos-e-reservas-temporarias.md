# ADR-001: Reserva Temporária de Agendamentos durante Ligações com PostgreSQL e Lock Pessimista

* **Status:** Aprovado
* **Data:** 2026-10-24
* **Autor:** [Edir Lucas da Silva Icety Braga]
* **Impacto:** Gestão de agendamentos, ciclo de vida das ligações e persistência no PostgreSQL

## 1. Contexto e Problema

A aplicação interpreta pedidos de agendamento feitos pelo cliente. O próximo passo é consultar a disponibilidade e garantir o horário escolhido enquanto o cliente ainda está em ligação.

Se duas pessoas escolherem o mesmo horário simultaneamente, ambas podem receber a informação de que ele está disponível. Além disso, o cliente precisa de tempo para confirmar sua escolha durante a conversa, sem que outra pessoa ocupe esse horário.

Precisamos garantir que:

1. Apenas um cliente consiga reservar determinado horário.
2. O horário fique reservado temporariamente enquanto a ligação estiver ativa.
3. A confirmação do cliente mantenha o agendamento após o encerramento da ligação.
4. Uma ligação encerrada sem confirmação, ou interrompida inesperadamente, não deixe o horário indisponível indefinidamente.
5. Os agendamentos sejam preservados após reiniciar a aplicação ou o banco de dados.

## 2. Opções Consideradas

* **Opção 1 (Reservar somente na confirmação):** Consultar a disponibilidade durante a conversa e gravar o agendamento apenas quando o cliente confirmar.
    * *Prós:* Fluxo simples, sem necessidade de controlar reservas temporárias.
    * *Contras:* Outra pessoa pode ocupar o horário durante a conversa, obrigando o cliente a escolher novamente na confirmação.

* **Opção 2 (Reserva temporária persistida com lock pessimista):** Registrar a reserva no PostgreSQL assim que o cliente escolher o horário. Usar lock pessimista para coordenar tentativas simultâneas e sinais periódicos de atividade da ligação para controlar a validade da reserva.
    * *Prós:* Protege o horário durante a conversa, impede reservas concorrentes e permite liberar horários de ligações interrompidas.
    * *Contras:* Exige controlar o ciclo de vida da reserva e integrar os sinais de atividade e encerramento da ligação.

* **Opção 3 (Manter uma transação aberta durante a ligação):** Bloquear o horário no banco até o cliente confirmar ou encerrar a conversa.
    * *Prós:* Mantém a exclusividade enquanto o bloqueio estiver ativo.
    * *Contras:* Mantém conexões e bloqueios por toda a ligação, aumentando a contenção e o risco de esgotar os recursos do banco.

## 3. Decisão

A opção escolhida foi a **Opção 2**.

Os agendamentos e as reservas temporárias serão persistidos em um **PostgreSQL executado no Docker**, com **Flyway** para versionar e aplicar as alterações do banco. O acesso aos dados usará repositórios **Spring Data JPA**, com lock pessimista declarado no repositório e transações gerenciadas pelo Spring.

Quando o cliente escolher um horário, a aplicação verificará a disponibilidade e criará a reserva temporária em uma única transação, usando **lock pessimista** para impedir que requisições concorrentes ocupem o mesmo horário. O bloqueio do banco durará apenas essa operação; durante a conversa, a reserva persistida manterá o horário indisponível.

Serão persistidas as datas e os horários escolhidos pelos clientes, sem cadastrar previamente todos os horários disponíveis. Nesta etapa, a agenda será única, com atendimentos de 30 minutos e inícios em `:00` ou `:30`, no fuso de Fortaleza.

O ciclo da reserva seguirá estas regras:

* **Ligação ativa:** A integração enviará um sinal de atividade (*heartbeat*) a cada 30 segundos. Cada sinal renovará a validade da ligação por dois minutos, mantendo sua reserva temporária.
* **Confirmação do cliente:** A reserva se tornará um agendamento confirmado e permanecerá válida após o encerramento da ligação.
* **Encerramento sem confirmação:** O horário será liberado imediatamente.
* **Ausência de sinais de atividade:** Após dois minutos sem sinal, a reserva temporária expirará e o horário ficará disponível novamente. Sinais atrasados não reativarão a ligação expirada.

Os intervalos de atividade e expiração serão configuráveis. A integração com o provedor de telefonia será feita em uma etapa posterior; esta implementação disponibiliza as operações necessárias para receber esses eventos.

## 4. Consequências

### Positivas

* **Exclusividade do horário:** Duas ligações não poderão reservar o mesmo horário simultaneamente.
* **Continuidade da conversa:** O cliente poderá confirmar sua escolha sem perder o horário para outra pessoa durante a ligação ativa.
* **Liberação automática:** Ligações encerradas ou interrompidas não manterão reservas temporárias indefinidamente.
* **Persistência:** Agendamentos confirmados serão preservados após reinicializações.
* **Evolução controlada do banco:** O Flyway manterá o histórico das alterações de estrutura.

### Negativas / Riscos Mitigados

* **Dependência dos eventos da ligação:** A aplicação precisa receber os sinais de atividade e encerramento para acompanhar a conversa.
* *Mitigação:* A expiração por ausência de sinais libera o horário mesmo quando o evento de encerramento não chega.

* **Interrupção dos sinais durante uma ligação válida:** Uma falha prolongada na comunicação pode liberar a reserva antes do fim da conversa.
* *Mitigação:* O prazo de dois minutos tolera a perda de sinais isolados e pode ser ajustado. Uma reserva expirada exige uma nova verificação de disponibilidade antes de confirmar.

* **Contenção em alterações simultâneas:** O lock pessimista pode fazer uma requisição aguardar outra operação da agenda.
* *Mitigação:* As transações serão curtas, sem chamadas de IA ou espera pela conversa, e terão tempo de espera limitado. A estratégia deverá ser reavaliada ao ampliar a quantidade de agendas ou o volume de acessos.
