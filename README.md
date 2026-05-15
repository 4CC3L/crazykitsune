# CRAZY KITSUNE

Prototipo multijugador web de CRAZY KITSUNE con backend en Spring Boot y frontend en Angular.

## Stack

- Backend: Spring Boot 3, WebSocket STOMP, REST, estado en memoria.
- Frontend: Angular standalone, SCSS, SockJS + STOMP.
- Concurrencia: salas en `ConcurrentHashMap` y bloqueo por sala con `ReentrantLock`.

## Estructura

- `backend/`: API REST, WebSockets, salas, turnos y motor de partida.
- `frontend/`: interfaz de reglas, lobby y mesa de juego.

## Flujo actual

1. Un jugador crea una sala.
2. Hasta 10 jugadores pueden unirse con codigo.
3. El host inicia la partida.
4. Cada jugador recibe 8 cartas.
5. La UI muestra:
   - reglas resumidas y referencia de reglas avanzadas,
   - sala de espera con lista de jugadores,
   - mesa con mano privada, descarte, mazo, log y acciones.

## Reglas automatizadas en esta version

- Jugar por valor o palo de la carta abierta.
- Jokers como comodin jugable.
- Robo de carta del mazo o del descarte.
- Carta `2`: salto de jugadores.
- Carta `8`: cambio de palo.
- Carta `J`: turno adicional.
- Carta `Q`: ataque de 2 cartas por carta jugada.
- Carta `K`: ataque de 3 cartas por carta jugada.
- Carta `A`: intercambio simple aleatorio con jugador objetivo.
- Trio de `6`: granada simple/doble.
- Victoria por mano vacia.

## Reglas visibles pero no automatizadas por completo aun

- Escaleras.
- Trio simple empalmado y ver cartas temporal.
- Operacion matematica.
- Ataque e intercambio a distancia.
- Anulacion de victoria, muerte subita, UIT y penalidades completas.

## Ejecutar localmente

### Backend

```powershell
cd backend
mvn spring-boot:run
```

Servidor en `http://localhost:8080`.

### Frontend

```powershell
cd frontend
npm start
```

Aplicacion en `http://localhost:4200`.

## Validacion realizada

- `mvn compile test-compile` en `backend`
- `npm run build` en `frontend`

## Siguiente iteracion sugerida

- Completar las reglas avanzadas del documento.
- Persistir salas y reconexion de jugadores.
- Agregar contador de tiempo por turno y penalidades automaticas.