# YouTube Watch Party

Watch YouTube videos together, in sync. Create a room, share the link or code, and everyone sees the same
video at the same moment. The host (and moderators) control playback; everyone else can chat and send
requests for the host to approve.

**Live demo URL:** _add your deployed URL here_

## Contents

1. [Features](#features)
2. [Tech stack](#tech-stack)
3. [Quick start (Docker)](#quick-start-docker)
4. [Run without Docker (development)](#run-without-docker-development)
5. [Try it with several users](#try-it-with-several-users)
6. [Architecture](#architecture)
7. [Roles and permissions](#roles-and-permissions)
8. [WebSocket protocol](#websocket-protocol)
9. [How synchronization works](#how-synchronization-works)
10. [Project structure](#project-structure)
11. [Configuration](#configuration)
12. [Deployment](#deployment)
13. [Tests](#tests)
14. [Design decisions and trade-offs](#design-decisions-and-trade-offs)
15. [Troubleshooting](#troubleshooting)

## Features

- Create a room (you become **Host**) or join one with a **link or 6-character code**
- Real-time sync of **play / pause / seek / change video** over WebSockets
- Roles: **Host, Moderator, Participant, Viewer**. The host can assign roles, remove people and transfer the host role
- Role rules are enforced **on the server**; the UI only mirrors them
- **Approval workflow**: participants can request play / pause / seek / change video, and a host or moderator approves or rejects
- Live participant list with roles and online status
- Chat (messages stored in PostgreSQL, last 50 shown to late joiners)
- **Persistent rooms** in PostgreSQL: a room, its video and position survive a server restart
- Reconnect grace period: refreshing the page keeps your seat and role (host included)
- Late joiners start at the correct position; periodic heartbeat corrects drift

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Vite, React Router, browser-native WebSocket |
| Video | YouTube IFrame Player API |
| Backend | Java 17, Spring Boot 3.3, Spring WebSocket (raw `TextWebSocketHandler`), Spring Data JPA |
| Database | PostgreSQL 16 |
| Packaging | Docker, Docker Compose, nginx (serves the frontend and proxies `/api` and `/ws`) |

## Quick start (Docker)

Requirements: Docker Desktop (or Docker Engine + the Compose plugin).

```bash
docker compose up --build
```

Then open **http://localhost:3000**.

The first build downloads dependencies and takes a few minutes. Afterwards it starts in seconds.

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| Backend API | http://localhost:8080/api/health |
| PostgreSQL | localhost:5432 (user `watchparty`, password `watchparty`, db `watchparty`) |

Stop with `Ctrl+C`, or `docker compose down`. Add `-v` to also delete the database volume.

All settings have defaults. To change ports or passwords: `cp .env.example .env` and edit it.

## Run without Docker (development)

Requirements: JDK 17+, Maven 3.9+, Node 18+ and a PostgreSQL database. The easiest database is the one from
the compose file:

```bash
# 1) Database only
docker compose up db

# 2) Backend (new terminal)  ->  http://localhost:8080
cd backend
mvn spring-boot:run

# 3) Frontend (new terminal) ->  http://localhost:5173
cd frontend
npm install
npm run dev
```

The backend defaults to `jdbc:postgresql://localhost:5432/watchparty` with user and password `watchparty`.
The Vite dev server proxies `/api` and `/ws` to port 8080, so no extra setup is needed.

## Try it with several users

1. Open http://localhost:3000, enter a name and click **Create a new room**. You are the Host.
2. Click **Copy invite link**. Open it in a **private/incognito window** (or another browser). Enter a different name. That user is a Participant.
3. In every window click **Join playback** once (browsers block autoplay until you click).
4. As Host: press play, pause, drag the timeline, paste another YouTube link and click **Play for everyone**. Everyone follows.
5. As Participant: the player is locked. Use **Request a change**; the Host sees it under **Requests** and can approve or reject.
6. As Host: use the dropdown next to a participant to make them **Moderator**, then watch their controls unlock. Try **Make host** and **Remove** too.
7. Refresh a window: you keep your seat and role. Close all windows and reopen the room link later: the room and video are still there.

## Architecture

```
 Browser (Host)         Browser (Moderator)      Browser (Participant)
 React + YouTube        React + YouTube          React + YouTube
      |  \                  |                         /  |
      |   +-----------------+------------------------+   |
      |            WebSocket  /ws    (real-time events)   |
      v                                                   v
 +---------------------------- nginx (frontend container) ----------------------------+
 |  serves the React build   |   /api -> backend   |   /ws -> backend (upgrade)       |
 +------------------------------------------------------------------------------------+
                                       |
                                       v
 +----------------------- Spring Boot (backend container) --------------------------+
 |  WatchPartyHandler    transport: connect / message / close                         |
 |        |                                                                          |
 |  MessageRouter        parse JSON envelope, dispatch by "type"                     |
 |        |                                                                          |
 |  Handlers             Membership | Playback | Role | Request | Chat | Ping         |
 |        |                                                                          |
 |  PermissionPolicy     role -> allowed actions  (checked BEFORE any change)        |
 |        |                                                                          |
 |  Services + Room      Room = live state + broadcast (in memory, one lock per room)|
 +-------------------------------------------|---------------------------------------+
                                             v
                                   PostgreSQL (rooms, chat_messages)
```

**The server is the single source of truth.** A client sends an *intent* ("pause"). The server identifies the
sender from the connection (never from the payload), checks the role, updates the room state and broadcasts
the new state to **everyone including the sender**. Every browser then renders what the server says.

Why WebSockets: after one HTTP upgrade handshake the connection stays open in both directions, so the server
can push a state change to all clients immediately, without polling.

### Request flow: host pauses

1. Host clicks pause in the YouTube player. The frontend sends `{"type":"pause","payload":{"time":42.1}}`.
2. `WatchPartyHandler` receives the frame and passes it to `MessageRouter`.
3. `PlaybackHandler` finds the sender (`Actor`) from the connection and calls `PermissionPolicy.check(role, PAUSE)`. A Participant stops here with a `FORBIDDEN` error.
4. `PlaybackService` validates the payload, `Room.applyPlayback` updates the state **and broadcasts** `sync_state` under one lock.
5. The new state is saved to PostgreSQL.
6. Every client's `useRoom` hook stores the new state; `YouTubePlayer` calls `pauseVideo()` and corrects the position if it drifted.

## Roles and permissions

| Action | Host | Moderator | Participant | Viewer |
|---|:-:|:-:|:-:|:-:|
| Play / pause / seek | yes | yes | no | no |
| Change video | yes | yes | no | no |
| Approve / reject requests | yes | yes | no | no |
| Assign roles | yes | no | no | no |
| Remove participants | yes | no | no | no |
| Transfer host | yes | no | no | no |
| Request a change | n/a | n/a | yes | yes |
| Chat | yes | yes | yes | yes |

- The creator becomes Host. Everyone who joins by link or code starts as **Participant**.
- Only the Host can assign **Moderator / Participant / Viewer**. *Viewer* behaves the same as Participant (a label to tell watchers apart).
- **Transfer host**: the old host becomes a Moderator.
- If the host leaves for good, the longest-present online Moderator (otherwise participant) becomes Host automatically.
- The creator holds a secret **host key** (stored in their browser). If they come back to an empty room, the key lets them take the Host seat again. A link alone never grants Host.

## WebSocket protocol

Endpoint: `/ws`. Every message is JSON with one envelope:

```json
{ "type": "seek", "payload": { "time": 120.5 } }
```

### Client to server

| type | payload | Allowed roles | Effect |
|---|---|---|---|
| `join_room` | `{ roomId, username, hostKey?, userId?, token? }` | anyone | Join; `userId`+`token` resume an old seat |
| `leave_room` | `{}` | anyone | Leave immediately |
| `play` | `{ time? }` | Host, Moderator | Start playback |
| `pause` | `{ time? }` | Host, Moderator | Pause |
| `seek` | `{ time }` | Host, Moderator | Jump to `time` (seconds) |
| `change_video` | `{ videoId }` | Host, Moderator | Load another video (11-char id) |
| `assign_role` | `{ userId, role }` | Host | `MODERATOR`, `PARTICIPANT` or `VIEWER` |
| `remove_participant` | `{ userId }` | Host | Kick |
| `transfer_host` | `{ userId }` | Host | Hand over the host role |
| `request_action` | `{ action, data }` | Participant, Viewer | `action`: `play`, `pause`, `seek`, `change_video` |
| `resolve_request` | `{ requestId, approve }` | Host, Moderator | Approve (runs the action) or reject |
| `chat` | `{ text }` | anyone | Max 500 characters |
| `ping` | `{}` | anyone | Keep-alive, answered with `pong` |

### Server to client

| type | payload | Sent to |
|---|---|---|
| `joined` | `{ roomId, userId, token, role, participants, state, chat, requests }` | the joining user |
| `sync_state` | `{ playState, currentTime, videoId, serverTime, cause, by }` | whole room |
| `user_joined` | `{ username, userId, role, participants }` | others in the room |
| `user_left` | `{ username, userId, participants }` | room |
| `presence_changed` | `{ participants }` | room (someone went offline / came back) |
| `role_assigned` | `{ userId, username, role, participants }` | room |
| `participant_removed` | `{ userId, username, participants }` | room |
| `removed` | `{ reason }` | the removed user (socket is then closed) |
| `host_changed` | `{ userId, username, previousHostId, participants }` | room |
| `action_requested` | `{ requestId, userId, username, action, data, ts }` | Host and Moderators |
| `requests_sync` | `{ requests }` | a user who just became Host/Moderator |
| `request_sent` | `{ requestId, action }` | the requester |
| `request_resolved` | `{ requestId, approve, action, userId, resolvedBy }` | Host, Moderators and the requester |
| `chat` | `{ id, userId, username, text, ts }` | room |
| `replaced` | `{}` | an old tab whose seat a newer tab took over |
| `error` | `{ code, message }` | the sender only |
| `pong` | `{}` | the sender |

Error codes: `BAD_REQUEST`, `FORBIDDEN`, `NOT_FOUND`, `NOT_IN_ROOM`, `RATE_LIMITED`, `INTERNAL`.

### REST (only for room creation)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/rooms` | Create a room; returns `{ code, hostKey }` |
| `GET` | `/api/rooms/{code}` | `200` if the room exists, otherwise `404` |
| `GET` | `/api/health` | Liveness check |

## How synchronization works

- **Server state:** `PlaybackState` stores `videoId`, `playing`, the position at the last change and the time of that change. The live position is computed on demand, so the server needs no per-second timer.
- **Late joiners:** `joined` includes the computed position, so a newcomer lands at the right spot.
- **Drift correction:** a scheduler re-broadcasts `sync_state` every 10 s for rooms that are playing. A client only seeks if it is more than 1.5 s off, which avoids stutter.
- **Echo-loop prevention:** changing the YouTube player from code also fires player events. For about 1.2 s after applying remote state those events are ignored, and play/pause events are only sent when they differ from the server state.
- **Seek detection:** the IFrame API has no seek event, so Host/Moderator clients poll `getCurrentTime()` every 500 ms and treat an unexpected jump as a seek.
- **Locked player:** Participants and Viewers get a transparent overlay over the player. This is only for convenience; the server rejects their commands anyway.
- **Autoplay:** browsers block autoplay with sound, so everyone clicks **Join playback** once.
- **Atomic updates:** state change and broadcast happen under the same room lock, so two simultaneous actions can never leave clients disagreeing with the server.

## Project structure

```
watchparty/
├── docker-compose.yml            PostgreSQL + backend + frontend
├── .env.example                  optional settings
├── README.md
├── backend/
│   ├── Dockerfile                multi-stage Maven build -> JRE image
│   ├── pom.xml
│   └── src/
│       ├── main/resources/application.properties
│       ├── main/java/com/watchparty/
│       │   ├── WatchPartyApplication.java
│       │   ├── config/
│       │   │   ├── AppProperties.java        typed "watchparty.*" settings
│       │   │   ├── WebSocketConfig.java      registers /ws, origin check, size/idle limits
│       │   │   ├── CorsConfig.java           CORS for /api
│       │   │   └── SchedulingConfig.java     shared scheduler
│       │   ├── controller/RoomController.java   POST/GET /api/rooms, /api/health
│       │   ├── entity/                       RoomEntity, ChatMessageEntity (JPA)
│       │   ├── repository/                   Spring Data repositories
│       │   ├── model/
│       │   │   ├── Room.java                 live room: members, playback, requests, broadcast
│       │   │   ├── Participant.java          seat: userId, token, role, connection
│       │   │   ├── PlaybackState.java        position + timestamp math
│       │   │   ├── PendingRequest.java, PlaybackSnapshot.java, RemovalResult.java
│       │   │   └── Role.java, Action.java    enums
│       │   ├── security/PermissionPolicy.java   role -> allowed actions
│       │   ├── exception/                    WsException, PermissionDeniedException
│       │   ├── service/
│       │   │   ├── RoomService.java          in-memory rooms + PostgreSQL persistence
│       │   │   ├── MembershipService.java    join / leave / reconnect grace
│       │   │   ├── PlaybackService.java      single code path for state changes
│       │   │   └── ScheduledTasks.java       heartbeat + old-room purge
│       │   └── websocket/
│       │       ├── WatchPartyHandler.java    transport only
│       │       ├── MessageRouter.java        JSON envelope -> handler by type
│       │       ├── Connection.java           one socket + its bound room/user
│       │       ├── Outbound.java             builds outgoing JSON
│       │       ├── Validation.java           payload validation
│       │       └── handlers/                 Membership, Playback, Role, Request, Chat, Ping
│       └── test/java/com/watchparty/         PermissionPolicyTest, PlaybackStateTest
└── frontend/
    ├── Dockerfile                    Vite build -> nginx
    ├── nginx.conf.template           serves SPA, proxies /api and /ws
    ├── package.json, vite.config.ts, tsconfig.json, index.html
    └── src/
        ├── main.tsx, App.tsx, styles.css
        ├── pages/        Home.tsx (create / join), Room.tsx (the room screen)
        ├── hooks/        useRoom.ts (WebSocket, reconnect, state reducer)
        ├── components/   YouTubePlayer, ParticipantList, RequestPanel, RequestsPanel,
        │                 VideoUrlInput, Chat, Toasts
        └── lib/          types, config, api, storage, time, youtube
```

## Configuration

Backend environment variables (defaults in `application.properties`):

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | HTTP / WebSocket port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/watchparty` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `watchparty` | Database credentials |
| `ALLOWED_ORIGINS` | `*` | Allowed browser origins, comma separated. Set your real site in production |
| `RECONNECT_GRACE_SECONDS` | `30` | How long a disconnected user keeps their seat |
| `ROOM_RETENTION_DAYS` | `7` | Inactive rooms are deleted after this many days |
| `DEFAULT_VIDEO_ID` | `M7lc1UVf-VE` | Video of a brand new room |

Frontend build variables (only needed when the backend is on a different host):

| Variable | Example |
|---|---|
| `VITE_API_URL` | `https://watchparty-api.onrender.com` |
| `VITE_WS_URL` | `wss://watchparty-api.onrender.com/ws` |

The database schema is created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

## Deployment

### Option A: any VPS with Docker (simplest)

```bash
git clone <your repo> && cd watchparty
cp .env.example .env        # set a strong POSTGRES_PASSWORD and ALLOWED_ORIGINS=https://your-domain
docker compose up -d --build
```

Put a TLS terminator in front of port 3000 (Caddy, Traefik or nginx with Let's Encrypt). The frontend then
uses `wss://` automatically because it derives the WebSocket address from the page address.

### Option B: Render (three pieces)

1. **PostgreSQL**: New > PostgreSQL. Note host, database, user and password.
2. **Backend**: New > Web Service > connect the repo, root directory `backend`, runtime **Docker**. Environment:
   - `DB_URL=jdbc:postgresql://<internal-host>:5432/<database>`
   - `DB_USER=<user>`, `DB_PASSWORD=<password>`
   - `ALLOWED_ORIGINS=https://<your-frontend>.onrender.com`
3. **Frontend**: New > Static Site, root directory `frontend`, build command `npm install && npm run build`, publish directory `dist`. Environment:
   - `VITE_API_URL=https://<your-backend>.onrender.com`
   - `VITE_WS_URL=wss://<your-backend>.onrender.com/ws`
   - Add a rewrite rule `/*` to `/index.html` so links like `/room/AB12CD` work on refresh.

Notes:
- Render uses a `postgres://` URL; Spring needs the **JDBC** form above, so enter host, database, user and password separately as shown.
- Free instances sleep after inactivity, so the first request can take 30+ seconds. A sleeping backend also drops WebSockets; the client reconnects automatically.
- Render terminates TLS, so browsers connect with `wss://`.

## Tests

```bash
cd backend && mvn test
```

`PermissionPolicyTest` covers the role matrix and `PlaybackStateTest` covers the position math.

## Design decisions and trade-offs

- **Raw WebSocket instead of STOMP.** One JSON envelope and a router are easier to reason about and explain than brokers and topics. Socket.IO is a Node library and does not apply to Spring.
- **Room owns state and broadcast.** `Room` methods mutate state and broadcast under one lock. That keeps clients and server consistent under concurrent actions. The cost is that sends happen inside the lock, which is acceptable because each session send is buffered by `ConcurrentWebSocketSessionDecorator` (it also cuts off clients that cannot keep up).
- **Thread safety of sockets.** `WebSocketSession.sendMessage` is not thread-safe; every session is wrapped in `ConcurrentWebSocketSessionDecorator`.
- **Identity from the connection, not the payload.** A client cannot claim to be someone else. Resuming a seat needs a random token that only that browser received.
- **Playback stored as position + timestamp**, not ticked every second. It scales better and is accurate enough for late joiners.
- **Hybrid persistence.** Durable data (rooms, video, position, chat) is in PostgreSQL. Live data (sockets, roles, pending requests) is in memory. Roles reset if the server restarts, because everyone disconnects anyway.
- **Single backend instance.** Rooms live in one JVM's memory. Horizontal scaling needs Redis Pub/Sub for cross-instance broadcast and shared room state in Redis, behind a WebSocket-aware load balancer. This is the natural next step.
- **`ddl-auto=update`** keeps setup simple. A production system would use Flyway or Liquibase migrations.
- **Known limits.** A removed user can rejoin with the link (there is no ban list). No authentication (names are free text). Roles are not persisted across a backend restart. Drift correction assumes the host's video plays steadily in real time.

## Troubleshooting

| Problem | Fix |
|---|---|
| Port 5432 already in use | Set `DB_PORT=5433` in `.env` (or stop your local Postgres) |
| Port 3000 or 8080 already in use | Set `WEB_PORT` in `.env`, or free the port |
| Backend keeps restarting | `docker compose logs backend`. Usually the database is not reachable or credentials differ from the volume's first start. Run `docker compose down -v` to reset the database |
| "Could not create a room" | The backend is not up yet. Wait a few seconds and check `http://localhost:8080/api/health` |
| Video does not start | Click **Join playback** (autoplay policy). Some videos disallow embedding; try another |
| Others are not in sync | Everyone must click **Join playback**; only Host/Moderator actions are broadcast |
| WebSocket fails behind a proxy | The proxy must forward the `Upgrade` and `Connection` headers (see `nginx.conf.template`) |
