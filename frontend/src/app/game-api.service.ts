import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable } from 'rxjs';

export interface RoomJoinResponse {
  roomCode: string;
  playerId: string;
  state: GameView;
}

export interface GameView {
  roomCode: string;
  playerId: string;
  playerName: string;
  started: boolean;
  host: boolean;
  currentTurn: boolean;
  winnerId: string | null;
  winnerName: string | null;
  matchRank: string | null;
  matchSuit: Suit | null;
  pendingDraw: number;
  drawPileCount: number;
  hand: CardView[];
  discard: CardView[];
  players: PlayerSummary[];
  log: string[];
  version: number;
}

export interface CardView {
  id: string;
  rank: string;
  suit: Suit | 'JOKER';
  label: string;
}

export interface PlayerSummary {
  id: string;
  name: string;
  cardCount: number;
  host: boolean;
  currentTurn: boolean;
  winner: boolean;
}

export interface RulesView {
  gameName: string;
  basics: string[];
  poweredCards: string[];
  implementationNotes: string[];
}

export interface RoomEvent {
  roomCode: string;
  version: number;
  message: string;
  at: string;
}

export type Suit = 'HEARTS' | 'DIAMONDS' | 'CLUBS' | 'SPADES';

@Injectable({ providedIn: 'root' })
export class GameApiService {
  private readonly http = inject(HttpClient);
  private client?: Client;
  private subscription?: StompSubscription;

  readonly backendUrl = 'http://localhost:8080';

  getRules(): Observable<RulesView> {
    return this.http.get<RulesView>(`${this.backendUrl}/api/rules`);
  }

  createRoom(playerName: string): Observable<RoomJoinResponse> {
    return this.http.post<RoomJoinResponse>(`${this.backendUrl}/api/rooms`, { playerName });
  }

  joinRoom(roomCode: string, playerName: string): Observable<RoomJoinResponse> {
    return this.http.post<RoomJoinResponse>(`${this.backendUrl}/api/rooms/${roomCode}/join`, { playerName });
  }

  getState(roomCode: string, playerId: string): Observable<GameView> {
    return this.http.get<GameView>(`${this.backendUrl}/api/rooms/${roomCode}/state`, {
      params: { playerId }
    });
  }

  connect(
    roomCode: string,
    onEvent: (event: RoomEvent) => void,
    onConnection: (connected: boolean) => void,
    onError: (message: string) => void
  ): void {
    this.disconnect();

    this.client = new Client({
      reconnectDelay: 5000,
      webSocketFactory: () => new SockJS(`${this.backendUrl}/ws`)
    });

    this.client.onConnect = () => {
      onConnection(true);
      this.subscription = this.client?.subscribe(`/topic/rooms/${roomCode}`, (message: IMessage) => {
        onEvent(JSON.parse(message.body) as RoomEvent);
      });
    };

    this.client.onStompError = (frame) => {
      onError(frame.headers['message'] || 'Fallo en la conexion realtime.');
    };

    this.client.onWebSocketClose = () => {
      onConnection(false);
    };

    this.client.activate();
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    void this.client?.deactivate();
    this.client = undefined;
  }

  startGame(roomCode: string, playerId: string): void {
    this.publish(`/app/rooms/${roomCode}/start`, { playerId });
  }

  play(roomCode: string, playerId: string, cardIds: string[], declaredSuit?: Suit, targetPlayerId?: string): void {
    this.publish(`/app/rooms/${roomCode}/play`, { playerId, cardIds, declaredSuit, targetPlayerId });
  }

  draw(roomCode: string, playerId: string, fromDiscard: boolean): void {
    this.publish(`/app/rooms/${roomCode}/draw`, { playerId, fromDiscard });
  }

  pass(roomCode: string, playerId: string): void {
    this.publish(`/app/rooms/${roomCode}/pass`, { playerId });
  }

  private publish(destination: string, body: object): void {
    if (!this.client?.connected) {
      throw new Error('La conexion realtime aun no esta lista.');
    }
    this.client.publish({ destination, body: JSON.stringify(body) });
  }
}