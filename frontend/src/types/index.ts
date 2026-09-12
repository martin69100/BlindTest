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
  previewUrl?: string;
}

export interface LeaderboardEntry {
  playerId: string;
  playerName: string;
  avatarUrl?: string;
  elo?: number;
  score: number;
  team?: 'BLUE' | 'RED';
}

export interface LobbyParticipant {
  userId: string;
  displayName: string;
  avatarUrl?: string;
  elo: number;
  isHost: boolean;
  isReady: boolean;
  team?: 'BLUE' | 'RED';
  lastGameScore?: number;
  lastGameRank?: number;
}

export interface LobbyData {
  code: string;
  hostId: string;
  hostName: string;
  themeId?: string;
  themeName?: string;
  roundsCount: number;
  gameMode?: 'BUZZER' | 'NO_BUZZER';
  teamMode?: 'INDIVIDUAL' | 'TEAMS';
  status: 'WAITING' | 'PLAYING' | 'FINISHED';
  activeGameId?: string;
  participants: Record<string, LobbyParticipant>;
  lastGameLeaderboard?: LeaderboardEntry[];
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
  isCustom?: boolean;
  lobbyCode?: string;
  gameMode?: 'BUZZER' | 'NO_BUZZER';
  teamMode?: 'INDIVIDUAL' | 'TEAMS';
  teamScores?: Record<string, number>;
  playerTeams?: Record<string, string>;
  player1Id?: string;
  player2Id?: string;
  player1Name?: string;
  player2Name?: string;
  playerScores?: Record<string, number>;
  leaderboard?: LeaderboardEntry[];
}

export interface WrongGuess {
  playerId: string;
  playerName: string;
  guess: string;
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
  currentScores?: {
    player1: number;
    player2: number;
  };
  playerScores?: Record<string, number>;
  teamScores?: Record<string, number>;
  leaderboard?: LeaderboardEntry[];
}

export interface FreeAnswerCorrectEvent {
  event: 'FREE_ANSWER_CORRECT';
  playerId: string;
  playerName: string;
  foundType: 'TITLE' | 'ARTIST';
  foundName: string;
  playerTeam?: string;
  playerScores: Record<string, number>;
  teamScores?: Record<string, number>;
  leaderboard?: LeaderboardEntry[];
}

export interface FreeAnswerWrongEvent {
  event: 'FREE_ANSWER_WRONG';
  playerId: string;
  playerName?: string;
  guess: string;
}

export interface StealOpenEvent {
  event: 'ANSWER_FAILED_STEAL_OPEN';
  failedPlayerId: string;
  failedPlayerName?: string;
  wrongGuess?: string;
  remainingAudioMs: number;
  titleFound?: boolean;
  artistFound?: boolean;
  firstFoundType?: 'TITLE' | 'ARTIST' | null;
  currentScores?: {
    player1: number;
    player2: number;
  };
  playerScores?: Record<string, number>;
  teamScores?: Record<string, number>;
  leaderboard?: LeaderboardEntry[];
}

export interface AnswerWrongEvent {
  event: 'ANSWER_WRONG';
  playerId: string;
  playerName: string;
  guess: string;
}

export interface RoundEndEvent {
  event: 'ROUND_END';
  roundNumber: number;
  track: RevealedTrack;
  scores: {
    player1: number;
    player2: number;
  };
  playerScores?: Record<string, number>;
  teamScores?: Record<string, number>;
  playerTeams?: Record<string, string>;
  leaderboard?: LeaderboardEntry[];
  titleFound?: boolean;
  artistFound?: boolean;
  titleFoundByPlayerId?: string;
  artistFoundByPlayerId?: string;
  titleFoundByName?: string;
  artistFoundByName?: string;
  isLastRound: boolean;
  isCustom?: boolean;
  lobbyCode?: string;
  gameMode?: 'BUZZER' | 'NO_BUZZER';
  teamMode?: 'INDIVIDUAL' | 'TEAMS';
  player1Id?: string;
  player2Id?: string;
  player1Name?: string;
  player2Name?: string;
  wrongGuesses?: WrongGuess[];
}

export interface RoundHistoryItem {
  roundNumber: number;
  title: string;
  artist: string;
  albumName?: string;
  albumCoverUrl?: string;
  previewUrl?: string;
  titleFound: boolean;
  artistFound: boolean;
  titleFoundByPlayerId?: string | null;
  artistFoundByPlayerId?: string | null;
  titleFoundByName?: string;
  artistFoundByName?: string;
  player1Score?: number;
  player2Score?: number;
  playerScores?: Record<string, number>;
  teamScores?: Record<string, number>;
  wrongGuesses?: WrongGuess[];
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
  isCustom?: boolean;
  lobbyCode?: string;
  gameMode?: 'BUZZER' | 'NO_BUZZER';
  teamMode?: 'INDIVIDUAL' | 'TEAMS';
  scores?: Record<string, number>;
  playerScores?: Record<string, number>;
  teamScores?: Record<string, number>;
  playerTeams?: Record<string, string>;
  leaderboard?: LeaderboardEntry[];
  roundHistory?: RoundHistoryItem[];
}

export interface MatchmakingStats {
  inQueue: number;
  inGame: number;
  activeMatches: number;
}

