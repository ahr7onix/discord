# Chronos

Aplicativo Android nativo de **agendamentos, calendário e lembretes recorrentes**.
Agenda pessoal offline, com regras de repetição que podem continuar indefinidamente,
dados somente no aparelho e nenhum envio para servidores.

> Especificação de origem: `Projeto_Chronos_Completo.pdf`. Este repositório implementa
> o escopo técnico e funcional desse documento.

---

## Configuração definida

| Item | Valor |
|------|-------|
| Plataforma | Android nativo (Kotlin + Jetpack Compose) |
| Armazenamento | Local no aparelho (Room + DataStore) |
| Notificações | Locais, somente no celular (AlarmManager) |
| Conectividade | Funções principais sem internet |
| Idioma | Português brasileiro |
| Recorrência | Ilimitada, com data final opcional |

## Stack

- **Kotlin 2.0**, **Jetpack Compose** + **Material 3** (tema escuro padrão, paleta violeta)
- **Room** (banco local) · **DataStore** (preferências)
- **AlarmManager** (alarmes exatos) · **WorkManager** (manutenção) · **Coroutines/Flow**
- **Navigation Compose** · `java.time` (desugaring) para cálculo de datas/fuso correto
- `minSdk 26` · `targetSdk 35` · `compileSdk 35`

## Arquitetura

```
com.chronos.app
├── data
│   ├── database   Room: ChronosDatabase, Converters, Migrations, seed
│   ├── entities   events, recurrence_rules, exceptions, notifications, history, categories
│   ├── dao        EventDao, Category/Notification/HistoryDao
│   └── repository ChronosRepository (expansão de ocorrências), BackupManager, Settings, Mappers
├── domain
│   ├── model      Event, RecurrenceRule, enums, Category
│   ├── recurrence RecurrenceEngine (motor), RecurrenceDescriber (texto pt-BR)
│   └── scheduler  EventScheduler (interface)
├── notifications  AlarmSchedulerImpl, AlarmReceiver, BootReceiver, NotificationHelper, actions
└── ui             home · calendar · events · statistics · settings (Compose + ViewModels)
```

Camadas: **UI → ViewModels → Repository → (DAO Room | RecurrenceEngine | Scheduler)**.
O domínio de recorrência é puro e sem dependências Android, por isso é testável em JVM.

## Motor de recorrência (o núcleo)

`RecurrenceEngine` guarda apenas a **regra** e calcula as próximas ocorrências **sob
demanda** — séries infinitas nunca são materializadas no banco. Todo o cálculo é feito
em horário de parede (`LocalDate`), e o agendador resolve a ocorrência escolhida para um
instante absoluto usando o fuso atual do aparelho no momento do agendamento.

Tipos suportados: diária, intervalo diário (a cada X dias), semanal, dias específicos,
intervalo semanal (a cada X semanas), quinzenal (15 dias ou 2 semanas), mensal por dia do
mês, mensal por posição (ex.: última sexta), intervalo mensal, anual, e sem data final.

Casos de borda tratados e **testados**:

| Caso | Comportamento |
|------|---------------|
| Dia 31 em meses curtos | `SKIP` (pula o mês, padrão) ou `LAST_DAY` (último dia) |
| 29 de fevereiro anual | só em anos bissextos (`SKIP`) ou 28/02 (`LAST_DAY`) |
| "5ª segunda" inexistente | mês é pulado |
| Horário de verão (gap/overlap) | resolvido via `ZonedDateTime` do fuso atual |
| Mudança de fuso / relógio | `BootReceiver` recalcula tudo (`TIME_SET`, `TIMEZONE_CHANGED`) |
| Reinício do aparelho | alarmes reprogramados no boot |

Alarmes auto-avançam: apenas a **próxima** ocorrência com lembrete futuro é armada; ao
disparar, o `AlarmReceiver` reprograma a seguinte. Isso mantém séries infinitas vivas sem
estourar os limites de alarme do Android. Identificadores de alarme são determinísticos
(evitam duplicatas) e a entrega degrada para alarme inexato quando o usuário revoga alarmes
exatos.

## Build

Requer o **Android SDK** (`compileSdk 35`) instalado e `local.properties` com `sdk.dir`,
ou abrir no **Android Studio** (Ladybug ou superior).

```bash
# APK de debug
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

# Testes unitários (motor de recorrência, modelo, descritor)
./gradlew test

# Testes instrumentados (Room + repositório) — precisa de device/emulador
./gradlew connectedAndroidTest
```

## Testes

- `app/src/test/.../recurrence/` — motor de recorrência e modelo (JVM, sem Android).
  Cobrem todos os casos obrigatórios da especificação (§12): evento único, diária/semanal/
  dias específicos, intervalos, mensal no dia 31, anual bissexto, repetição infinita,
  contagem/data final, janelas e próxima ocorrência.
- `app/src/androidTest/` — banco Room, expansão de ocorrências, skip e conclusão/histórico.

## Privacidade

Dados pessoais permanecem no aparelho. Nenhum dado é enviado a servidores. Backup e
restauração são locais, em arquivo JSON escolhido pelo usuário (SAF). Exclusões e operações
destrutivas pedem confirmação na interface.

## Permissões

- `POST_NOTIFICATIONS` (Android 13+) — solicitada na primeira abertura.
- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` — lembretes no horário exato; se revogada, o
  app avisa e usa alarme inexato.
- `RECEIVE_BOOT_COMPLETED` — reprogramar alarmes após reiniciar.

## Status

Projeto completo e **compilável**. Verificado com AGP 8.7.3 + SDK 35 + Gradle 8.11.1:

- `./gradlew assembleDebug` gera `app/build/outputs/apk/debug/app-debug.apk` (~18 MB),
  instalável (pacote `com.chronos.app`, `MainActivity` lançável, minSdk 26 / targetSdk 35).
- `./gradlew testDebugUnitTest` passa: **31 testes, 0 falhas** (motor de recorrência,
  modelo de domínio e descritor).

Os testes instrumentados (`connectedAndroidTest`) precisam de um device/emulador.
