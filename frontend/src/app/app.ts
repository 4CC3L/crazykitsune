import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  type GameView,
  GameApiService,
  type PlayerSummary,
  type RoomJoinResponse,
  type RulesView,
  type Suit
} from './game-api.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly api = inject(GameApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly playerName = signal('');
  readonly joinCode = signal('');
  readonly declaredSuit = signal<Suit>('HEARTS');
  readonly targetPlayerId = signal('');
  readonly section = signal<'rules' | 'lobby' | 'table'>('lobby');
  readonly rules = signal<RulesView | null>(null);
  readonly state = signal<GameView | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly activity = signal('');
  readonly connected = signal(false);
  readonly selectedCardIds = signal<string[]>([]);

  readonly suits: Suit[] = ['HEARTS', 'DIAMONDS', 'CLUBS', 'SPADES'];
  readonly suitLabels: Record<Suit, string> = {
    HEARTS: 'Corazones',
    DIAMONDS: 'Diamantes',
    CLUBS: 'Treboles',
    SPADES: 'Espadas'
  };

  readonly detailedRuleSections = [
    {
      title: 'Setup rapido',
      bullets: [
        'Taliban Loco hereda reglas de Ocho Loco y usa 3 barajas con jokers.',
        'Se reparten 8 cartas por jugador y la primera carta del mazo abre la ronda.',
        'Se juega por valor o por palo de la carta abierta.'
      ]
    },
    {
      title: 'Cartas con poder',
      bullets: [
        'A intercambia cartas con otro jugador.',
        '2 salta jugadores.',
        '8 cambia el palo activo.',
        'J da turnos adicionales.',
        'Q y K generan ataques de robo.',
        'JOKER funciona como comodin y defensa.'
      ]
    },
    {
      title: 'Combinaciones del documento',
      bullets: [
        'Duo, trio, trio simple, escalera y operacion matematica.',
        'Granada con 6, ver cartas con 9 y ataques a distancia.',
        'El prototipo actual automatiza el nucleo jugable y deja visibles las reglas avanzadas para la siguiente iteracion.'
      ]
    },
    {
      title: 'Victoria y penalidades',
      bullets: [
        'La referencia completa incluye mano vacia, jokers en mano y Taliban Loco.',
        'Tambien contempla anulacion de victoria, muerte subita y penalidades por anuncios omitidos.',
        'En esta version la victoria automatica es por mano vacia.'
      ]
    }
  ];

  readonly joined = computed(() => this.state() !== null);
  readonly inGame = computed(() => this.state()?.started ?? false);
  readonly selectedCards = computed(() => {
    const game = this.state();
    const selected = new Set(this.selectedCardIds());
    return game?.hand.filter((card) => selected.has(card.id)) ?? [];
  });
  readonly opponents = computed(() => {
    const game = this.state();
    return game?.players.filter((player) => player.id !== game.playerId) ?? [];
  });
  readonly canPlay = computed(() => {
    const game = this.state();
    return Boolean(game?.started && game.currentTurn && this.selectedCardIds().length > 0 && !game.winnerId);
  });
  readonly needsSuit = computed(() => this.selectedCards().some((card) => card.rank === '8' || card.rank === 'JOKER'));
  readonly needsTarget = computed(() => this.selectedCards().some((card) => card.rank === 'A'));

  constructor() {
    this.loadRules();
    this.destroyRef.onDestroy(() => this.api.disconnect());
  }

  async createRoom(): Promise<void> {
    await this.runBusy(async () => {
      const response = await firstValueFrom(this.api.createRoom(this.playerName().trim()));
      await this.acceptRoom(response);
      this.activity.set(`Sala ${response.roomCode} creada.`);
    });
  }

  async joinRoom(): Promise<void> {
    await this.runBusy(async () => {
      const response = await firstValueFrom(this.api.joinRoom(this.joinCode().trim().toUpperCase(), this.playerName().trim()));
      await this.acceptRoom(response);
      this.activity.set(`Te uniste a la sala ${response.roomCode}.`);
    });
  }

  async refreshState(): Promise<void> {
    const game = this.state();
    if (!game) {
      return;
    }

    try {
      const nextState = await firstValueFrom(this.api.getState(game.roomCode, game.playerId));
      this.state.set(nextState);
      this.pruneSelection(nextState);
      if (nextState.started) {
        this.section.set('table');
      }
    } catch (error) {
      this.error.set(this.describeError(error));
    }
  }

  startGame(): void {
    this.sendRealtime(() => {
      const game = this.requireState();
      this.api.startGame(game.roomCode, game.playerId);
    });
  }

  playSelected(): void {
    this.sendRealtime(() => {
      const game = this.requireState();
      this.api.play(
        game.roomCode,
        game.playerId,
        this.selectedCardIds(),
        this.needsSuit() ? this.declaredSuit() : undefined,
        this.needsTarget() ? this.targetPlayerId() || undefined : undefined
      );
      this.selectedCardIds.set([]);
    });
  }

  draw(fromDiscard: boolean): void {
    this.sendRealtime(() => {
      const game = this.requireState();
      this.api.draw(game.roomCode, game.playerId, fromDiscard);
      this.selectedCardIds.set([]);
    });
  }

  pass(): void {
    this.sendRealtime(() => {
      const game = this.requireState();
      this.api.pass(game.roomCode, game.playerId);
      this.selectedCardIds.set([]);
    });
  }

  leaveRoom(): void {
    this.api.disconnect();
    this.connected.set(false);
    this.activity.set('Sesion cerrada.');
    this.error.set('');
    this.state.set(null);
    this.selectedCardIds.set([]);
    this.targetPlayerId.set('');
    this.section.set('lobby');
  }

  toggleCard(cardId: string): void {
    const current = this.selectedCardIds();
    this.selectedCardIds.set(
      current.includes(cardId) ? current.filter((id) => id !== cardId) : [...current, cardId]
    );
  }

  setSection(section: 'rules' | 'lobby' | 'table'): void {
    this.section.set(section);
  }

  setPlayerName(value: string): void {
    this.playerName.set(value);
  }

  setJoinCode(value: string): void {
    this.joinCode.set(value.toUpperCase());
  }

  setDeclaredSuit(value: Suit): void {
    this.declaredSuit.set(value);
  }

  setTargetPlayerId(value: string): void {
    this.targetPlayerId.set(value);
  }

  trackPlayer(_: number, player: PlayerSummary): string {
    return player.id;
  }

  trackText(_: number, value: string): string {
    return value;
  }

  private async loadRules(): Promise<void> {
    try {
      this.rules.set(await firstValueFrom(this.api.getRules()));
    } catch (error) {
      this.error.set(this.describeError(error));
    }
  }

  private async acceptRoom(response: RoomJoinResponse): Promise<void> {
    this.api.connect(
      response.roomCode,
      async (event) => {
        this.activity.set(event.message);
        await this.refreshState();
      },
      (connected) => this.connected.set(connected),
      (message) => this.error.set(message)
    );

    this.state.set(response.state);
    this.pruneSelection(response.state);
    this.section.set(response.state.started ? 'table' : 'lobby');
    await this.refreshState();
  }

  private pruneSelection(state: GameView): void {
    const validIds = new Set(state.hand.map((card) => card.id));
    this.selectedCardIds.set(this.selectedCardIds().filter((id) => validIds.has(id)));
    const currentTargetIsValid = state.players.some((player) => player.id === this.targetPlayerId());
    if (!currentTargetIsValid) {
      this.targetPlayerId.set('');
    }
  }

  private requireState(): GameView {
    const game = this.state();
    if (!game) {
      throw new Error('Primero debes unirte a una sala.');
    }
    return game;
  }

  private sendRealtime(action: () => void): void {
    this.error.set('');
    try {
      action();
    } catch (error) {
      this.error.set(this.describeError(error));
    }
  }

  private async runBusy(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await action();
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.busy.set(false);
    }
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || error.error?.detail || error.message || 'No se pudo completar la operacion.';
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'No se pudo completar la operacion.';
  }
}
