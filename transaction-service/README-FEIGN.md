# Communication Account / Transaction — question C.3

## Démarrage

1. Redémarrer `AccountServiceApplication` sur le port 8081 : il expose désormais le débit.
2. Démarrer `TransactionServiceApplication` sur le port 8082.
3. Ouvrir `requests.http` dans IntelliJ. Exécuter d’abord la récupération des vrais comptes.

Les comptes utilisent des UUID générés : ne plus employer les UUID fictifs de la première version.
Le bean de démonstration récupère les comptes par Feign et crée trois transactions. Ces transactions débitent réellement les comptes.
Mettre `bank.demo-data.enabled=false` pour désactiver ce bean. Avec la base transaction H2 en mémoire, un redémarrage recrée les données de démonstration et peut débiter à nouveau les comptes si account-service n’a pas redémarré.

## Flux métier

Les connecteurs REST, GraphQL et MCP appellent la même méthode `TransactionService.saveTransaction`.

1. Vérifier le montant positif, le plafond et la précision décimale.
2. `AccountClient` (OpenFeign) appelle `GET /accounts/{id}`.
3. Vérifier le statut ACTIVE, la devise et le solde suffisant.
4. Persister la transaction en PENDING et valider cette transaction locale.
5. Appeler `POST /accounts/{id}/debits` avec `operationId = transaction.id`.
6. account-service verrouille le compte, revérifie le solde et les règles, puis enregistre le débit et son identifiant dans sa propre transaction SQL.
7. Marquer la transaction COMPLETED.

Les soldes d’Account sont maintenant des BigDecimal, avec 8 décimales stockées, comme les montants de Transaction.
La consultation avant débit ne suffit pas à empêcher deux dépenses concurrentes : le verrou et la revérification côté compte sont nécessaires.

## Tolérance aux pannes

`AccountClient` porte les annotations Resilience4J `@CircuitBreaker` et les méthodes `default` de fallback. Les lectures utilisent aussi `@Retry`. `AccountCommunicationConfig` configure les registres et active leurs aspects Spring.
Le circuit breaker est appliqué par Spring AOP au proxy Feign. Les fallbacks restent dans AccountClient et renvoient des erreurs explicites ; ils ne créent pas de faux compte. Avec Spring Boot 4, les aspects Resilience4J Spring sont enregistrés explicitement, sans auto-configuration Boot 3.

- Connexion Feign : timeout 1 seconde.
- Lecture Feign : timeout 2 secondes.
- Lectures : deux tentatives maximum, espacées de 200 ms, uniquement sur erreurs réseau ou HTTP 5xx.
- Débits : pas de retry automatique applicatif ; une reprise réutilise l’identifiant enregistré.
- Circuit breaker : fenêtre de 5 appels logiques, au moins 3 appels, ouverture à 50 % d’échecs, attente de 10 secondes, puis 2 appels autorisés en HALF_OPEN.
- Les rejets HTTP 4xx ne sont pas comptés comme des pannes de disponibilité.
- En cas de panne ou de circuit ouvert : réponse REST HTTP 503. GraphQL et MCP utilisent leurs formats d’erreur respectifs.

`bank.account-service.url` vaut `${ACCOUNT_SERVICE_URL:http://localhost:8081}`.
Sous Docker, utiliser `ACCOUNT_SERVICE_URL=http://account-service:8081`.
Eureka reste désactivé pour cette étape ; le client utilise l’URL explicite. Le passage à la découverte sera traité avec les services techniques.

## États et reprise

- PENDING : transaction enregistrée, résultat du débit non confirmé.
- COMPLETED : débit confirmé.
- FAILED : refus explicite du débit par account-service.

Une coupure après le débit ne permet pas de déduire si le compte a été débité. La transaction reste PENDING et son UUID est indiqué dans le message d’erreur REST. On peut aussi la retrouver avec GET /transactions, champ processingStatus.

Après rétablissement du service, appeler :

```http
POST http://localhost:8082/transactions/UUID-DE-LA-TRANSACTION/retry
```

La reprise réutilise le même operationId. account-service conserve le reçu de débit et ne débite pas deux fois. Une transaction déjà COMPLETED est simplement retournée.
Ne pas refaire POST /transactions pour résoudre une réponse incertaine : cela créerait une nouvelle opération avec un nouvel identifiant.
Les transactions et les comptes ont deux bases distinctes ; il ne s’agit pas d’une transaction distribuée atomique. La reprise est explicite, sans ordonnanceur automatique. Avec H2 en mémoire, les transactions et les reçus de débit disparaissent à l’arrêt des services ; une reprise après redémarrage nécessite des bases persistantes.

## Vérifications

- Tests HTTP des trois opérations REST, GraphQL et MCP.
- Tests OpenFeign via un serveur HTTP local, sans appeler les comptes de développement.
- Vérifications : compte absent, bloqué, devise incorrecte, solde insuffisant, timeout et HTTP 503.
- Ouverture du circuit et absence d’appel HTTP supplémentaire lorsque le circuit est ouvert.
- Simulation d’une réponse perdue après débit, puis reprise sans double débit.
- Tests d’account-service sur sa vraie base H2 : débit idempotent via HTTP, précision et concurrence sans découvert.

La publication Kafka et les virements restent des étapes distinctes.