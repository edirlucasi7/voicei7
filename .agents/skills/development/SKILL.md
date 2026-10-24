---
name: development
description: Desenvolver e refatorar o codigo Java deste projeto de agendamento, mantendo regras de dominio junto ao estado e respeitando os limites da arquitetura hexagonal. Use ao alterar modelos, casos de uso e seus testes.
---

# Desenvolvimento do dominio

Ao alterar uma regra, identifique qual objeto possui os dados que determinam a decisao. Coloque nesse objeto o comportamento e as invariantes que dependem do seu estado. Records podem ter metodos de dominio; mantenha a imutabilidade retornando uma nova instancia quando houver mudanca.

Prefira uma operacao com significado de negocio a uma sequencia externa de getters, condicionais e reconstrucao do objeto. Encapsule tanto a decisao de permitir a mudanca quanto sua aplicacao. Evite apenas mover o `if` para um predicado e deixar a alteracao do estado espalhada pelo servico.

Neste projeto, o construtor de `AppointmentRequest` garante a coerencia entre horario e periodo, usando `DayPeriod.from`. Quando o horario esta ausente, preserva o periodo extraido.

O caso de uso coordena as portas e as operacoes do dominio. Interpretacao do texto e conversao da resposta da IA ficam no adaptador; HTTP fica no controller. Nao acrescente extratores manuais para compensar a IA sem uma necessidade demonstrada e um escopo definido para essa interpretacao complementar. Nao leve Spring, HTTP, chamadas externas ou parsing de linguagem natural para `AppointmentRequest` apenas para aproximar comportamento do estado. Mantenha DTOs de transporte separados do modelo de dominio.

Declare records de entrada e saida em arquivos proprios, um tipo por arquivo; nao os aninhe em controllers, interfaces de casos de uso ou outras classes de producao. DTOs HTTP ficam em `adapter/in/web/dto`; resultados de casos de uso ficam na camada de aplicacao. Ao alterar esse codigo, procure outras declaracoes internas equivalentes e mantenha essa organizacao.

Use nomes em ingles nos tipos e contratos do codigo, com responsabilidades de negocio explicitas, como `AppointmentRepository` e `CallSessionRepository`. Para persistencia, prefira repositorios Spring Data JPA, `@Lock(PESSIMISTIC_WRITE)` e transacoes declarativas nos adaptadores. Nao implemente repositorios JDBC manuais nem abstracoes genericas como `AgendaStore` ou `AgendaTransaction`. Mantenha entidades JPA separadas do dominio e preserve a protecao concorrente de horarios ainda nao persistidos.

Ao revisar uma mudanca, procure regras equivalentes nos servicos e adaptadores do fluxo afetado. Centralize a regra no objeto responsavel sem expandir a refatoracao para partes sem relacao. Preserve os contratos de API e as regras existentes, salvo mudanca solicitada.

Na formatacao Java, deixe uma linha em branco entre o fechamento de um bloco (`}`) e a proxima instrucao ou declaracao. Por exemplo, separe o fim de um `if` da declaracao ou do `return` seguinte. Preserve a continuidade de `else`, `catch` e `finally`; nao acrescente linhas entre chaves consecutivas que apenas encerram blocos, nem antes de pontuacao que conclui a mesma expressao.

Organize o corpo dos metodos em blocos coesos, separados por uma linha em branco quando houver mudanca de responsabilidade ou assunto. Agrupe a declaracao de uma variavel com seu uso imediato, inclusive um `return` que usa o resultado recem-obtido. Por exemplo, separe a validacao inicial da chamada ao parser, sem criar separacoes artificiais dentro do processamento. Preparacao, chamada, validacao do resultado e retorno nao exigem blocos separados quando formam uma unica sequencia coesa. Mantenha juntas as chamadas de uma mesma cadeia fluente. Evite espacamento mecanico a cada instrucao ou etapa; priorize a leitura do fluxo.

Teste as decisoes e invariantes diretamente no dominio: estados permitidos e recusados, preservacao dos demais campos e imutabilidade. Mantenha testes de caso de uso e HTTP que verifiquem a coordenacao e o contrato observavel. Evite testes que apenas reproduzam a estrutura interna do metodo.

Execute `./mvnw test` na raiz do repositorio. O perfil `local-ai` depende de Ollama e serve para verificar o modelo real quando a tarefa exigir essa integracao.
