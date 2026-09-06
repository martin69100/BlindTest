export interface User {
  id: string;
  googleId: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  elo: number;
  createdAt: string;
  lastActiveAt: string;
}

export interface Theme {
  id: string;
  code: string;
  name: string;
  description?: string;
  iconUrl?: string;
  trackCount: number;
}

export interface UserThemeStats {
  id: string;
  theme: Theme;
  soloGamesPlayed: number;
  soloCorrectAnswers: number;
  versusCorrectAnswers: number;
  bonusCorrectAnswers: number;
  masteryPoints: number;
}

export interface RevealedTrack {
  title: string;
  artist: string;
  albumName?: string;
  albumCoverUrl?: string;
}

export interface RoundStartEvent {
  event: 'ROUND_START';
  gameId: string;
  roundId: string;
  roundNumber: number;
  totalRounds: number;
  previewUrl: string;
  durationSeconds: number;
  serverTimestamp: number;
  player1Id?: string;
  player2Id?: string;
  player1Name?: string;
  player2Name?: string;
}

export interface PlayerBuzzedEvent {
  event: 'PLAYER_BUZZED';
  buzzerPlayerId: string;
  buzzerName: string;
  inputTimeoutSeconds: number;
  remainingAudioMs: number;
}

export interface FirstAnswerCorrectEvent {
  event: 'FIRST_ANSWER_CORRECT';
  playerId: string;
  foundType: 'TITLE' | 'ARTIST';
  foundName: string;
  bonusDurationSeconds: number;
  currentScores: {
    player1: number;
    player2: number;
  };
}

export interface StealOpenEvent {
  event: 'ANSWER_FAILED_STEAL_OPEN';
  failedPlayerId: string;
  remainingAudioMs: number;
  titleFound?: boolean;
  artistFound?: boolean;
  firstFoundType?: string;
  currentScores?: {
    player1: number;
    player2: number;
  };
}

export interface RoundEndEvent {
  event: 'ROUND_END';
  roundNumber: number;
  track: RevealedTrack;
  scores: {
    player1: number;
    player2: number;
  };
  isLastRound: boolean;
  player1Id?: string;
  player2Id?: string;
  player1Name?: string;
  player2Name?: string;
}

export interface MatchFinishedEvent {
  event: 'MATCH_FINISHED' | 'SOLO_MATCH_FINISHED';
  player1Score?: number;
  player2Score?: number;
  finalScore?: number;
  forfeit?: boolean;
  winnerId?: string;
  player1EloChange?: number;
  player2EloChange?: number;
  player1NewElo?: number;
  player2NewElo?: number;
  player1Id?: string;
  player2Id?: string;
  player1Name?: string;
  player2Name?: string;
}
