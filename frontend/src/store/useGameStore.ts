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

  // Scores
  player1Score: number;
  player2Score: number;

  // Révélation & Fin
  revealedTrack: RevealedTrack | null;
  isLastRound: boolean;
  matchResult: MatchFinishedEvent | null;

  // Actions
  initGame: (gameId: string, isSolo?: boolean) => void;
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
  inputTimeoutSeconds: 5,
  bonusDurationSeconds: 10,

  player1Score: 0,
  player2Score: 0,

  revealedTrack: null,
  isLastRound: false,
  matchResult: null,

  initGame: (gameId, isSolo = false) => {
    set({
      gameId,
      isSolo,
      phase: 'WAITING',
      roundNumber: 0,
      player1Score: 0,
      player2Score: 0,
      revealedTrack: null,
      matchResult: null,
    });
  },

  onRoundStart: (event) => {
    set({
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
    });
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
    set({
      phase: 'PLAYING',
      buzzerPlayerId: null,
      buzzerName: null,
      remainingAudioMs: event.remainingAudioMs,
    });
  },

  onRoundEnd: (event) => {
    set({
      phase: 'REVEAL',
      revealedTrack: event.track,
      player1Score: event.scores.player1,
      player2Score: event.scores.player2,
      isLastRound: event.isLastRound,
    });
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
      revealedTrack: null,
      matchResult: null,
      player1Score: 0,
      player2Score: 0,
    });
  },
}));
