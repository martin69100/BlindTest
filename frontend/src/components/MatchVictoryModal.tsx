import React, { useEffect, useState, useRef } from 'react';
import confetti from 'canvas-confetti';
import {
  Trophy,
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  Dumbbell,
  Award,
  Check,
  X,
  Play,
  Pause,
  Music,
  Disc,
  Shield,
  Users,
} from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { customLobbyService } from '../services/api';

export const MatchVictoryModal: React.FC = () => {
  const {
    phase,
    matchResult,
    player1Id,
    player2Id,
    player1Name,
    player2Name,
    player1Score,
    player2Score,
    roundHistory,
    resetGame,
    isSolo,
    isCustom,
    lobbyCode,
    activeLobby,
    leaderboard,
    returnToCustomLobby,
    totalRounds,
  } = useGameStore();
  const { user, refreshProfile } = useAuthStore();

  const [playingRoundNumber, setPlayingRoundNumber] = useState<number | null>(null);
  const [isReturning, setIsReturning] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (phase === 'FINISHED') {
      confetti({
        particleCount: 120,
        spread: 80,
        origin: { y: 0.6 },
      });
      refreshProfile();
    }
  }, [phase, refreshProfile]);

  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
    };
  }, []);

  if (phase !== 'FINISHED') return null;

  const isPlayer2 = !isSolo && !isCustom && Boolean(user && player2Id && user.id === player2Id);
  const myPlayerId = isPlayer2 ? player2Id : (user?.id || player1Id);
  const myScore = isPlayer2 ? player2Score : player1Score;
  const opponentScore = isPlayer2 ? player1Score : player2Score;
  const opponentDisplayName = isSolo
    ? 'Adversaire'
    : (isPlayer2 ? player1Name : player2Name) || 'Adversaire';

  const isForfeit = matchResult?.forfeit === true;

  // En mode personnalisé multijoueur
  const userRankIndex = isCustom && user && leaderboard?.length > 0
    ? leaderboard.findIndex((e) => e.playerId === user.id)
    : -1;
  const userRank = userRankIndex !== -1 ? userRankIndex + 1 : 1;
  const isWinner = isCustom
    ? userRank === 1
    : matchResult && (
        (matchResult.winnerId === user?.id) ||
        (Boolean(user) && myScore > opponentScore)
      );
  const isPodium = isCustom && userRank <= 3;
  const isDraw = !isCustom && myScore === opponentScore;

  const eloDelta = isPlayer2
    ? (matchResult?.player2EloChange ?? 0)
    : (matchResult?.player1EloChange ?? 0);

  // Score de l'utilisateur
  const myCustomEntry = leaderboard?.find((e) => e.playerId === user?.id);
  const displayScore = isCustom && myCustomEntry
    ? myCustomEntry.score
    : (myScore ?? 0);

  const maxScore = totalRounds * 2;

  // Calcul du résumé global des titres et artistes trouvés
  const titlesFoundCount = roundHistory.filter((r) =>
    isSolo
      ? r.titleFound
      : Boolean(r.titleFoundByPlayerId && r.titleFoundByPlayerId === myPlayerId)
  ).length;

  const artistsFoundCount = roundHistory.filter((r) =>
    isSolo
      ? r.artistFound
      : Boolean(r.artistFoundByPlayerId && r.artistFoundByPlayerId === myPlayerId)
  ).length;

  const togglePlayTrack = (roundNum: number, previewUrl?: string) => {
    if (!previewUrl) return;

    if (playingRoundNumber === roundNum) {
      audioRef.current?.pause();
      setPlayingRoundNumber(null);
    } else {
      if (!audioRef.current) {
        audioRef.current = new Audio();
        audioRef.current.onended = () => setPlayingRoundNumber(null);
      }
      audioRef.current.src = previewUrl;
      audioRef.current.play().catch((err) => console.warn('Lecture extrait audio impossible :', err));
      setPlayingRoundNumber(roundNum);
    }
  };

  const handleExit = () => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
    resetGame();
  };

  const handleReturnToLobby = async () => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
    setIsReturning(true);
    const code = activeLobby?.code || lobbyCode;
    if (code && user) {
      try {
        await customLobbyService.returnToLobby(code, user.id);
      } catch (e) {
        console.warn('Erreur signalement retour salon :', e);
      }
    }
    returnToCustomLobby();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-3 sm:p-4 animate-in fade-in duration-200">
      <div className="bg-dark-900 border border-brand-500/40 rounded-3xl p-6 sm:p-7 max-w-2xl w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Entête avec badge et titre */}
        <div className="text-center shrink-0">
          <div className={`w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-3 shadow-xl ${
            isSolo
              ? 'bg-gradient-to-tr from-cyan-400 to-indigo-500 shadow-cyan-500/30'
              : isCustom
              ? isWinner
                ? 'bg-gradient-to-tr from-amber-400 to-yellow-500 shadow-amber-500/30'
                : isPodium
                ? 'bg-gradient-to-tr from-indigo-400 to-brand-500 shadow-indigo-500/30'
                : 'bg-gradient-to-tr from-slate-400 to-slate-600 shadow-slate-500/30'
              : isWinner
              ? 'bg-gradient-to-tr from-amber-400 to-yellow-500 shadow-amber-500/30'
              : isDraw
              ? 'bg-gradient-to-tr from-slate-400 to-slate-500 shadow-slate-500/30'
              : 'bg-gradient-to-tr from-rose-500 to-amber-600 shadow-rose-500/30'
          }`}>
            {isSolo ? (
              displayScore >= maxScore * 0.7 ? (
                <Trophy className="w-8 h-8 text-dark-950" />
              ) : (
                <Dumbbell className="w-8 h-8 text-dark-950" />
              )
            ) : isWinner ? (
              <Trophy className="w-8 h-8 text-dark-950" />
            ) : isPodium ? (
              <Award className="w-8 h-8 text-white" />
            ) : (
              <Users className="w-8 h-8 text-white" />
            )}
          </div>

          <h2 className="text-2xl font-black text-white tracking-tight mb-1">
            {isSolo
              ? isForfeit
                ? 'Entraînement interrompu'
                : 'Session d’entraînement terminée !'
              : isCustom
              ? isWinner
                ? 'VICTOIRE !'
                : isPodium
                ? `PODIUM ! (${userRank}e place)`
                : `FIN DE PARTIE (${userRank}e place)`
              : isDraw
              ? 'Match Nul !'
              : isWinner
              ? 'VICTOIRE !'
              : 'DÉFAITE'}
          </h2>

          {/* Badge ELO protégé pour les salons privés */}
          {isCustom && (
            <div className="inline-flex items-center space-x-1.5 bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 text-xs font-bold px-3 py-1 rounded-full my-1.5">
              <Shield className="w-3.5 h-3.5 text-indigo-400" />
              <span>Partie Personnalisée &bull; Rangs ELO Inchangés</span>
            </div>
          )}

          {/* Classement Multijoueur Complet si Custom */}
          {isCustom && leaderboard && leaderboard.length > 0 ? (
            <div className="bg-dark-950/70 border border-slate-800 rounded-2xl p-3 my-2.5">
              <div className="flex items-center justify-between mb-2 px-1">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 flex items-center space-x-1">
                  <Trophy className="w-3.5 h-3.5 text-amber-400" />
                  <span>Classement Final ({leaderboard.length} joueurs)</span>
                </span>
                <span className="text-[10px] text-slate-500 font-mono">Scores &bull; Rangs ELO</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                {leaderboard.map((player, idx) => {
                  const isMe = user && player.playerId === user.id;
                  return (
                    <div
                      key={player.playerId}
                      className={`p-2 rounded-xl border flex items-center justify-between transition-all ${
                        isMe
                          ? 'bg-brand-600/20 border-brand-500/70 shadow-sm'
                          : 'bg-dark-900 border-slate-800'
                      }`}
                    >
                      <div className="flex items-center space-x-2 min-w-0">
                        <span className="w-6 h-6 rounded-lg bg-dark-800 border border-slate-700 text-xs font-black flex items-center justify-center shrink-0">
                          {idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : idx + 1}
                        </span>
                        <div className="min-w-0">
                          <p className={`text-xs font-black truncate max-w-[110px] ${isMe ? 'text-white' : 'text-slate-300'}`}>
                            {player.playerName} {isMe && '(Vous)'}
                          </p>
                          <p className="text-[10px] font-mono text-amber-400/80">
                            {player.elo || 1000} ELO
                          </p>
                        </div>
                      </div>
                      <span className="text-sm font-black font-mono text-brand-400 bg-dark-800 px-2.5 py-0.5 rounded-lg border border-slate-700 shrink-0">
                        {player.score} pts
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : !isCustom ? (
            /* Scores et ELO pour le Versus Classé / Solo */
            <div className="flex items-center justify-center gap-4 my-3">
              <div className="bg-dark-950/70 px-4 py-2 rounded-xl border border-slate-800">
                <span className="text-[11px] text-slate-400 block font-semibold">Votre score</span>
                <span className="text-xl font-black text-brand-400 font-mono">{displayScore} pts</span>
              </div>

              {!isSolo && (
                <>
                  <span className="text-slate-600 font-bold text-sm">VS</span>
                  <div className="bg-dark-950/70 px-4 py-2 rounded-xl border border-slate-800">
                    <span className="text-[11px] text-slate-400 block font-semibold truncate max-w-[100px]">
                      {opponentDisplayName}
                    </span>
                    <span className="text-xl font-black text-slate-400 font-mono">{opponentScore ?? 0} pts</span>
                  </div>
                </>
              )}

              {!isSolo && matchResult && (
                <div className="bg-dark-950/70 px-4 py-2 rounded-xl border border-slate-800 flex items-center space-x-1.5">
                  {eloDelta >= 0 ? (
                    <TrendingUp className="w-4 h-4 text-emerald-400" />
                  ) : (
                    <TrendingDown className="w-4 h-4 text-rose-400" />
                  )}
                  <div>
                    <span className="text-[10px] text-slate-400 block font-semibold">Variation ELO</span>
                    <span className={`text-sm font-black font-mono ${eloDelta >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {eloDelta >= 0 ? `+${eloDelta}` : eloDelta} pts
                    </span>
                  </div>
                </div>
              )}
            </div>
          ) : null}

          {/* Cartes de synthèse 3 colonnes */}
          <div className="grid grid-cols-3 gap-2.5 my-2">
            <div className="bg-dark-950/60 border border-slate-800/80 rounded-xl p-2 text-center">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Titres trouvés</span>
              <span className="text-base font-black text-emerald-400 font-mono">{titlesFoundCount} / {roundHistory.length || totalRounds}</span>
            </div>
            <div className="bg-dark-950/60 border border-slate-800/80 rounded-xl p-2 text-center">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Artistes trouvés</span>
              <span className="text-base font-black text-emerald-400 font-mono">{artistsFoundCount} / {roundHistory.length || totalRounds}</span>
            </div>
            <div className="bg-dark-950/60 border border-slate-800/80 rounded-xl p-2 text-center">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Taux de réussite</span>
              <span className="text-base font-black text-brand-400 font-mono">
                {Math.round(((titlesFoundCount + artistsFoundCount) / Math.max(1, (roundHistory.length || totalRounds) * 2)) * 100)}%
              </span>
            </div>
          </div>
        </div>

        {/* Section défilante du récapitulatif complet de toutes les manches */}
        <div className="flex-1 overflow-y-auto my-3 pr-1 space-y-2">
          <div className="flex items-center justify-between mb-2 px-1">
            <h3 className="text-xs font-black text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
              <Disc className="w-3.5 h-3.5 text-brand-400" />
              <span>Récapitulatif complet des manches ({roundHistory.length})</span>
            </h3>
            <span className="text-[10px] text-slate-500">
              Cliquez sur la pochette pour réécouter
            </span>
          </div>

          {roundHistory.length === 0 ? (
            <p className="text-xs text-slate-500 text-center py-4">
              Aucun détail de manche enregistré.
            </p>
          ) : (
            roundHistory.map((round) => {
              const iGotTitle = isSolo
                ? round.titleFound
                : Boolean(round.titleFoundByPlayerId && round.titleFoundByPlayerId === myPlayerId);

              const iGotArtist = isSolo
                ? round.artistFound
                : Boolean(round.artistFoundByPlayerId && round.artistFoundByPlayerId === myPlayerId);

              const otherGotTitle = !isSolo && Boolean(
                round.titleFoundByPlayerId && round.titleFoundByPlayerId !== myPlayerId
              );
              const otherGotArtist = !isSolo && Boolean(
                round.artistFoundByPlayerId && round.artistFoundByPlayerId !== myPlayerId
              );

              const titleFinder = isCustom
                ? round.titleFoundByName || 'Un joueur'
                : opponentDisplayName;
              const artistFinder = isCustom
                ? round.artistFoundByName || 'Un joueur'
                : opponentDisplayName;

              const isPlayingThis = playingRoundNumber === round.roundNumber;

              return (
                <div
                  key={round.roundNumber}
                  className="flex items-center justify-between p-2.5 sm:p-3 rounded-2xl bg-dark-950/70 border border-slate-800/80 hover:border-slate-700 transition-colors"
                >
                  <div className="flex items-center space-x-2.5 sm:space-x-3 min-w-0 flex-1 pr-2">
                    {/* Numéro de manche */}
                    <span className="w-6 h-6 rounded-lg bg-dark-800 border border-slate-700/80 text-[11px] font-black text-slate-400 flex items-center justify-center shrink-0">
                      {round.roundNumber}
                    </span>

                    {/* Pochette avec bouton écoute */}
                    <div className="relative w-11 h-11 rounded-xl overflow-hidden shrink-0 border border-slate-700 group">
                      {round.albumCoverUrl ? (
                        <img
                          src={round.albumCoverUrl}
                          alt={round.title}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full bg-dark-800 flex items-center justify-center text-slate-600">
                          <Music className="w-5 h-5" />
                        </div>
                      )}
                      {round.previewUrl && (
                        <button
                          onClick={() => togglePlayTrack(round.roundNumber, round.previewUrl)}
                          className={`absolute inset-0 flex items-center justify-center bg-black/60 transition-opacity cursor-pointer ${
                            isPlayingThis ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                          }`}
                          title={isPlayingThis ? 'Mettre en pause' : 'Écouter un extrait de 30s'}
                        >
                          {isPlayingThis ? (
                            <Pause className="w-4 h-4 text-emerald-400" />
                          ) : (
                            <Play className="w-4 h-4 text-white fill-white ml-0.5" />
                          )}
                        </button>
                      )}
                    </div>

                    {/* Titre & Artiste */}
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-bold text-white truncate">{round.title}</p>
                      <p className="text-[11px] text-brand-400 font-medium truncate">{round.artist}</p>
                      {round.albumName && (
                        <p className="text-[10px] text-slate-500 italic truncate hidden sm:block">
                          {round.albumName}
                        </p>
                      )}
                      {round.wrongGuesses && round.wrongGuesses.length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-1">
                          {round.wrongGuesses
                            .filter((w) => isSolo || w.playerId !== myPlayerId)
                            .map((w, wIdx) => (
                              <span
                                key={wIdx}
                                className="text-[10px] text-rose-400/90 bg-rose-500/10 border border-rose-500/20 px-1.5 py-0.5 rounded flex items-center gap-1"
                                title={`${w.playerName} a proposé : ${w.guess}`}
                              >
                                <span className="text-slate-500">{isSolo ? 'Tentative :' : `${w.playerName} :`}</span>
                                <span className="line-through font-semibold text-rose-300">« {w.guess} »</span>
                              </span>
                            ))}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Badges Titre (0/1 ou 1/1) et Artiste (0/1 ou 1/1) */}
                  <div className="flex items-center space-x-1.5 shrink-0">
                    {/* Badge Titre */}
                    <div
                      className={`px-2 py-1 rounded-lg border text-[10px] font-bold flex items-center space-x-1 ${
                        iGotTitle
                          ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                          : 'bg-dark-900 border-slate-800 text-slate-500'
                      }`}
                      title={
                        iGotTitle
                          ? 'Vous avez trouvé le titre (1/1)'
                          : otherGotTitle
                          ? `Trouvé par ${titleFinder} (0/1 pour vous)`
                          : 'Titre non trouvé (0/1)'
                      }
                    >
                      <span>Titre</span>
                      <span className="font-mono">{iGotTitle ? '1/1' : '0/1'}</span>
                      {iGotTitle ? (
                        <Check className="w-3 h-3 text-emerald-400 stroke-[3]" />
                      ) : (
                        <X className="w-3 h-3 text-rose-500/60" />
                      )}
                    </div>

                    {/* Badge Artiste */}
                    <div
                      className={`px-2 py-1 rounded-lg border text-[10px] font-bold flex items-center space-x-1 ${
                        iGotArtist
                          ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                          : 'bg-dark-900 border-slate-800 text-slate-500'
                      }`}
                      title={
                        iGotArtist
                          ? "Vous avez trouvé l'artiste (1/1)"
                          : otherGotArtist
                          ? `Trouvé par ${artistFinder} (0/1 pour vous)`
                          : 'Artiste non trouvé (0/1)'
                      }
                    >
                      <span>Artiste</span>
                      <span className="font-mono">{iGotArtist ? '1/1' : '0/1'}</span>
                      {iGotArtist ? (
                        <Check className="w-3 h-3 text-emerald-400 stroke-[3]" />
                      ) : (
                        <X className="w-3 h-3 text-rose-500/60" />
                      )}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Boutons d'action en bas */}
        <div className="pt-2 shrink-0 border-t border-slate-800/80">
          {isCustom ? (
            <button
              onClick={handleReturnToLobby}
              disabled={isReturning}
              className="w-full bg-gradient-to-r from-indigo-600 via-brand-600 to-rose-600 hover:from-indigo-500 hover:via-brand-500 hover:to-rose-500 text-white font-extrabold py-3.5 px-6 rounded-2xl flex items-center justify-center space-x-2 transition-transform active:scale-98 shadow-lg shadow-indigo-600/30 cursor-pointer"
            >
              <Users className="w-4 h-4" />
              <span>{isReturning ? 'Retour au salon en cours...' : 'Retourner au Salon Privé (Relancer)'}</span>
            </button>
          ) : (
            <button
              onClick={handleExit}
              className="w-full bg-brand-600 hover:bg-brand-500 text-white font-bold py-3.5 px-6 rounded-2xl flex items-center justify-center space-x-2 transition-transform active:scale-98 shadow-lg shadow-brand-600/30 cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Retour au salon principal</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
