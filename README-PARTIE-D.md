# Partie D — Services techniques

## Modules et ports

| Module | Port | Fonction |
|---|---:|---|
| discovery-service | 8761 | Annuaire Eureka autonome |
| config-service | 9999 | Spring Cloud Config Server, profil native |
| account-service | 8081 | Comptes et débits |
| transaction-service | 8082 | Transactions et appels Feign |
| gateway-service | 8888 | Routage Spring Cloud Gateway WebFlux via Eureka |

La racine est maintenant un agrégateur Maven : `mvn package` compile les cinq modules et exécute leurs tests.
Les modules conservent les versions du projet : Java 21, Spring Boot 4.1.2 et Spring Cloud 2025.1.4.

## Ordre de démarrage

Discovery → Config → Account → Transaction → Gateway.

Eureka possède sa configuration locale et ne dépend pas de Config Server.
Config Server s’enregistre dans Eureka, mais ne se configure pas lui-même via Config Server.
Les autres applications importent obligatoirement leur configuration depuis Config Server, puis s’enregistrent dans Eureka.
La configuration est récupérée par URL fixe au démarrage ; les appels métier et les routes Gateway utilisent ensuite Eureka.

Depuis la racine du projet, avec Java 21, Maven et PowerShell 7 :

```powershell
mvn package
./scripts/Start-BankServices.ps1
```

Le script attend la disponibilité de chaque service et l’enregistrement d’Account dans Eureka avant de démarrer Transaction.
Il refuse d’utiliser un port occupé et ne ferme pas les services déjà ouverts dans IntelliJ.
Il lance les jars en arrière-plan, avec les logs et PID dans `.runtime/`.

Pour arrêter uniquement les processus lancés par ce script, dans l’ordre inverse :

```powershell
./scripts/Stop-BankServices.ps1
```

Dans IntelliJ, démarrer les cinq classes `*Application` manuellement dans le même ordre. Vérifier `/actuator/health/readiness` et l’enregistrement Eureka avant de passer à la suivante.

## Configuration partagée

`config-repo/application.properties` contient :

```properties
bank.transaction.max-amount=1000
```

Le dossier est intégré comme ressource dans le jar de Config Server. Il fonctionne hors ligne et sans chemin absolu propre à une machine.
Le plafond n’est plus recopié dans les propriétés locales de TransactionService : sa valeur est obligatoire et provient de Config Server.

Consulter la configuration effective fournie au client :

```http
GET http://localhost:9999/transaction-service/default
```

La réponse contient `propertySources` et `bank.transaction.max-amount=1000`.
Pour modifier la configuration embarquée : modifier le dossier `config-repo`, reconstruire Config Server, puis redémarrer Config et les clients concernés.
Pour lire directement un dossier externe sans reconstruire le serveur, définir `BANK_CONFIG_REPO=file:C:/chemin/absolu/config-repo/` avant de démarrer Config Server. Après modification, redémarrer les clients pour recharger la valeur ; aucun refresh à chaud n’est annoncé.

## Gateway

| Point d’entrée | Destination |
|---|---|
| `/accounts` et `/accounts/**` | `lb://account-service`, chemin conservé |
| `/transactions` et `/transactions/**` | `lb://transaction-service`, chemin conservé |
| `/account-service/**` | `lb://account-service`, premier segment retiré |
| `/transaction-service/**` | `lb://transaction-service`, premier segment retiré |

Exemples :

```http
GET http://localhost:8888/accounts
GET http://localhost:8888/transactions
POST http://localhost:8888/account-service/graphql
POST http://localhost:8888/transaction-service/graphql
POST http://localhost:8888/account-service/mcp
POST http://localhost:8888/transaction-service/mcp
```

Les préfixes permettent de distinguer les deux endpoints `/graphql` et les deux endpoints `/mcp`.
Les sessions et messages MCP sont transmis au service cible. Avec plusieurs instances MCP, une stratégie d’affinité ou un état partagé devra être ajoutée ; cette étape utilise une instance de chaque service.

## OpenFeign avec Eureka

`AccountClient` utilise le nom `account-service`. En l’absence d’URL explicite, Spring Cloud LoadBalancer récupère l’instance depuis Eureka.
La protection Resilience4J déjà réalisée reste active autour du client.
`ACCOUNT_SERVICE_URL` peut toujours forcer une URL directe, notamment pour les tests ; ne pas la définir pour tester la découverte.

Les bases H2 et la création de données de démonstration restent inchangées. Les transactions de démonstration débitent les vrais comptes.
Utiliser `-NoDemoData` sur le script ou `bank.demo-data.enabled=false` pour désactiver leur création.

## Paramètres d’environnement

| Variable | Valeur par défaut |
|---|---|
| EUREKA_SERVER_URL | http://localhost:8761/eureka/ |
| CONFIG_SERVER_URL | http://localhost:9999 |
| BANK_CONFIG_REPO | classpath:/config-repo/ |
| ACCOUNT_SERVICE_URL | vide, découverte Eureka |

Les ports peuvent être changés par les arguments du script ou les variables `DISCOVERY_PORT`, `CONFIG_PORT`, `GATEWAY_PORT` pour les modules techniques.
Les tests isolés désactivent explicitement Config Server et Eureka ; les applications normales gardent l’import Config Server obligatoire.

## Vérifications

```powershell
mvn test
./scripts/Test-BankArchitecture.ps1
```

Le second script lance les cinq jars sur des ports distincts de ceux du développement, vérifie les enregistrements Eureka, le plafond, les routes REST/GraphQL/MCP et un débit réel via Feign.
Il vérifie aussi la réponse 503 quand le compte devient indisponible, puis arrête tous ses propres processus.
Le résultat est écrit dans `.runtime/part-d-verification.json`.

## Références utilisées

- https://github.com/charly-zoungrana/TP2-Ecom-microservices : modules techniques, ports et routage `lb://`.
- https://github.com/charly-zoungrana/config-repo-tp-ecom-ms : fichier partagé `application.properties`, paramètres Eureka et Actuator.

Le dépôt de configuration de cet exercice est local, afin de ne pas modifier les dépôts de référence ni d’en dépendre au démarrage.
Docker, Kafka, Keycloak et l’assistant IA restent les parties suivantes de l’épreuve.