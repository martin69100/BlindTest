import { create } from 'zustand';
import type {
  MatchFinishedEvent,
  PlayerBuzzedEvent,
  RevealedTrack,
  RoundEndEvent,
  RoundStartEvent,
} from '../types';

export type GamePhase = 'LOBBY' | 'WAITING' | 'PLAYING' | 'BUZZED' | 'BONUS' | 'REVEAL' | 'FINISHED';

interface GameState {
  gameId: string | null;
  isSolo: boolean;
  phase: GamePhase;
  roundNumber: number;
  totalRounds: number;
  audioUrl: string | null;
  remainingAudioMs: number;

  // Buzzer & Saisie
  buzzerPlayerId: string | null;
  buzzerName: string | null;
  firstFoundType: 'TITLE' | 'ARTIST' | null;
  firstFoundName: string | null;
  inputTimeoutSeconds: number;
  bonusDurationSeconds: number;
  failedPlayerIds: string[];

  // Joueurs & Scores
  player1Id: string | null;
  player2Id: string | null;
  player1Name: string | null;
  player2Name: string | null;
  player1Score: number;
  player2Score: number;

  // Révélation & Fin
  revealedTrack: RevealedTrack | null;
  isLastRound: boolean;
  matchResult: MatchFinishedEvent | null;

  // Actions
  initGame: (
    gameId: string,
    isSolo?: boolean,
    player1Id?: string,
    player2Id?: string,
    player1Name?: string,
    player2Name?: string
  ) => void;
  onRoundStart: (event: RoundStartEvent) => void;
  onPlayerBuzzed: (event: PlayerBuzzedEvent) => void;
  onFirstAnswerCorrect: (event: any) => void;
  onStealOpen: (event: any) => void;
  onRoundEnd: (event: RoundEndEvent) => void;
  onMatchFinished: (event: MatchFinishedEvent) => void;
  resetGame: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  gameId: null,
  isSolo: false,
  phase: 'LOBBY',
  roundNumber: 0,
  totalRounds: 10,
  audioUrl: null,
  remainingAudioMs: 20000,

  buzzerPlayerId: null,
  buzzerName: null,
  firstFoundType: null,
  firstFoundName: null,
  inputTimeoutSeconds: 8,
  bonusDurationSeconds: 10,
  failedPlayerIds: [],

  player1Id: null,
  player2Id: null,
  player1Name: null,
  player2Name: null,
  player1Score: 0,
  player2Score: 0,

  revealedTrack: null,
  isLastRound: false,
  matchResult: null,

  initGame: (gameId, isSolo = false, player1Id, player2Id, player1Name, player2Name) => {
    set({
      gameId,
      isSolo,
      phase: 'WAITING',
      roundNumber: 0,
      player1Score: 0,
      player2Score: 0,
      revealedTrack: null,
      matchResult: null,
      player1Id: player1Id || null,
      player2Id: player2Id || null,
      player1Name: player1Name || null,
      player2Name: player2Name || null,
      failedPlayerIds: [],
    });
  },

  onRoundStart: (event) => {
    set((state) => ({
      phase: 'PLAYING',
      roundNumber: event.roundNumber,
      totalRounds: event.totalRounds,
      audioUrl: event.previewUrl,
      remainingAudioMs: event.durationSeconds * 1000,
      buzzerPlayerId: null,
      buzzerName: null,
      firstFoundType: null,
      firstFoundName: null,
      revealedTrack: null,
      failedPlayerIds: [],
      player1Id: event.player1Id || state.player1Id,
      player2Id: event.player2Id || state.player2Id,
      player1Name: event.player1Name || state.player1Name,
      player2Name: event.player2Name || state.player2Name,
    }));
  },

  onPlayerBuzzed: (event) => {
    set({
      phase: 'BUZZED',
      buzzerPlayerId: event.buzzerPlayerId,
      buzzerName: event.buzzerName,
      inputTimeoutSeconds: event.inputTimeoutSeconds,
      remainingAudioMs: event.remainingAudioMs,
    });
  },

  onFirstAnswerCorrect: (event) => {
    set({
      phase: 'BONUS',
      firstFoundType: event.foundType,
      firstFoundName: event.foundName,
      bonusDurationSeconds: event.bonusDurationSeconds,
      player1Score: event.currentScores.player1,
      player2Score: event.currentScores.player2,
    });
  },

  onStealOpen: (event) => {
    set((state) => ({
      phase: 'PLAYING',
      buzzerPlayerId: null,
      buzzerName: null,
      remainingAudioMs: event.remainingAudioMs,
      failedPlayerIds: state.failedPlayerIds.includes(event.failedPlayerId)
        ? state.failedPlayerIds
        : [...state.failedPlayerIds, event.failedPlayerId],
      ...(event.currentScores
        ? {
            player1Score: event.currentScores.player1,
            player2Score: event.currentScores.player2,
          }
        : {}),
      ...(event.firstFoundType ? { firstFoundType: event.firstFoundType } : {}),
    }));
  },

  onRoundEnd: (event) => {
    set((state) => ({
      phase: 'REVEAL',
      revealedTrack: event.track,
      player1Score: event.scores.player1,
      player2Score: event.scores.player2,
      isLastRound: event.isLastRound,
      player1Id: event.player1Id || state.player1Id,
      player2Id: event.player2Id || state.player2Id,
      player1Name: event.player1Name || state.player1Name,
      player2Name: event.player2Name || state.player2Name,
    }));
  },

  onMatchFinished: (event) => {
    set((state) => {
      const p1 = event.player1Score ?? event.finalScore ?? state.player1Score ?? 0;
      const p2 = event.player2Score ?? state.player2Score ?? 0;
      return {
        phase: 'FINISHED',
        matchResult: event,
        player1Score: p1,
        player2Score: p2,
        player1Id: event.player1Id || state.player1Id,
        player2Id: event.player2Id || state.player2Id,
        player1Name: event.player1Name || state.player1Name,
        player2Name: event.player2Name || state.player2Name,
      };
    });
  },

  resetGame: () => {
    set({
      gameId: null,
      phase: 'LOBBY',
      roundNumber: 0,
      audioUrl: null,
      buzzerPlayerId: null,
      buzzerName: null,
      firstFoundType: null,
      firstFoundName: null,
      revealedTrack: null,
      matchResult: null,
      player1Id: null,
      player2Id: null,
      player1Name: null,
      player2Name: null,
      failedPlayerIds: [],
      player1Score: 0,
      player2Score: 0,
    });
  },
}));
