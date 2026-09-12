import React, { useEffect, useState } from 'react';
import { ArrowRight, Trophy, Disc, Clock, Check, X, Users } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';

export const RoundRevealCard: React.FC = () => {
  const {
    gameId,
    phase,
    revealedTrack,
    lastRoundResult,
    roundNumber,
    totalRounds,
    isLastRound,
    isSolo,
    isCustom,
    teamMode,
    teamScores,
    playerTeams,
    leaderboard,
    player1Id,
    player2Id,
    player1Name,
    player2Name,
    roundWrongGuesses,
  } = useGameStore();
  const { user } = useAuthStore();
  const [secondsRemaining, setSecondsRemaining] = useState(5);

  const handleNext = () => {
    if (!gameId || !isSolo) return;
    wsService.sendNextRound(gameId);
  };

  // Détection de la touche Espace pour passer immédiatement à la suite (uniquement en solo)
  useEffect(() => {
    if (phase !== 'REVEAL' || !isSolo) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const activeTag = document.activeElement?.tagName?.toLowerCase();
      if (activeTag === 'input' || activeTag === 'textarea') return;

      if (e.code === 'Space' || e.key === ' ') {
        e.preventDefault();
        handleNext();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [phase, gameId, isSolo]);

  // Compte à rebours automatique de 5 secondes pour enchaîner
  useEffect(() => {
    if (phase !== 'REVEAL') return;

    setSecondsRemaining(5);
    const interval = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          if (isSolo) {
            handleNext();
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [phase, roundNumber, isSolo]);

  if (phase !== 'REVEAL' || !revealedTrack) return null;

  // Détermination de qui a trouvé le titre et l'artiste sur cette manche
  const isPlayer2 = !isSolo && !isCustom && Boolean(user && player2Id && user.id === player2Id);
  const myPlayerId = isPlayer2 ? player2Id : (user?.id || player1Id);
  const opponentName = (isPlayer2 ? player1Name : player2Name) || 'Adversaire';

  const userGotTitle = isSolo
    ? Boolean(lastRoundResult?.titleFound)
    : Boolean(lastRoundResult?.titleFoundByPlayerId && lastRoundResult.titleFoundByPlayerId === myPlayerId);

  const userGotArtist = isSolo
    ? Boolean(lastRoundResult?.artistFound)
    : Boolean(lastRoundResult?.artistFoundByPlayerId && lastRoundResult.artistFoundByPlayerId === myPlayerId);

  const otherGotTitle = !isSolo && Boolean(
    lastRoundResult?.titleFoundByPlayerId && lastRoundResult.titleFoundByPlayerId !== myPlayerId
  );
  const otherGotArtist = !isSolo && Boolean(
    lastRoundResult?.artistFoundByPlayerId && lastRoundResult.artistFoundByPlayerId !== myPlayerId
  );

  const titleFinderName = isCustom
    ? lastRoundResult?.titleFoundByName || 'Un joueur'
    : opponentName;

  const artistFinderName = isCustom
    ? lastRoundResult?.artistFoundByName || 'Un joueur'
    : opponentName;

  const allWrongGuesses = lastRoundResult?.wrongGuesses || roundWrongGuesses || [];
  const opponentWrongGuesses = isSolo
    ? []
    : allWrongGuesses.filter((g) => g.playerId !== myPlayerId);
  const myWrongGuesses = isSolo
    ? allWrongGuesses
    : allWrongGuesses.filter((g) => g.playerId === myPlayerId);

  return (
    <div className="w-full max-w-md mx-auto bg-dark-900 border border-slate-700/80 rounded-3xl p-6 shadow-2xl backdrop-blur-xl animate-in zoom-in-95 duration-300">
      <div className="flex items-center justify-between mb-4">
        <span className="text-[11px] font-bold tracking-wider uppercase text-brand-400 bg-brand-500/10 px-3 py-1 rounded-full border border-brand-500/20">
          Manche {roundNumber} / {totalRounds} terminée
        </span>

        {/* Chrono auto-advance */}
        <span className="flex items-center space-x-1 text-xs font-mono text-slate-400 bg-dark-800 px-2.5 py-1 rounded-lg border border-slate-700">
          <Clock className="w-3.5 h-3.5 text-brand-400 animate-spin" style={{ animationDuration: '4s' }} />
          <span>{secondsRemaining}s</span>
        </span>
      </div>

      <div className="flex flex-col items-center">
        {revealedTrack.albumCoverUrl ? (
          <img
            src={revealedTrack.albumCoverUrl}
            alt={revealedTrack.title}
            className="w-40 h-40 rounded-2xl shadow-xl border border-slate-700 object-cover mb-3"
          />
        ) : (
          <div className="w-40 h-40 rounded-2xl bg-dark-800 border border-slate-700 flex items-center justify-center mb-3 text-slate-500">
            <Disc className="w-16 h-16 animate-spin" />
          </div>
        )}

        <h3 className="text-xl font-black text-white text-center tracking-tight mb-0.5">
          {revealedTrack.title}
        </h3>
        <p className="text-sm font-semibold text-brand-400 text-center mb-1">
          {revealedTrack.artist}
        </p>
        {revealedTrack.albumName && (
          <p className="text-xs text-slate-400 italic text-center mb-3">
            Album : {revealedTrack.albumName}
          </p>
        )}

        {/* Récapitulatif de la manche : Titre (0/1 ou 1/1) et Artiste (0/1 ou 1/1) */}
        <div className="w-full bg-dark-950/80 border border-slate-800 rounded-2xl p-3 mb-4">
          <div className="grid grid-cols-2 gap-2.5">
            {/* Titre */}
            <div
              className={`p-2.5 rounded-xl border flex flex-col items-center justify-center transition-all ${
                userGotTitle
                  ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300 shadow-sm shadow-emerald-500/10'
                  : 'bg-dark-900 border-slate-800 text-slate-400'
              }`}
            >
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Titre</span>
              <div className="flex items-center space-x-1.5 my-0.5">
                {userGotTitle ? (
                  <Check className="w-4 h-4 text-emerald-400 stroke-[3]" />
                ) : (
                  <X className="w-4 h-4 text-rose-400 stroke-[3]" />
                )}
                <span className={`text-base font-black font-mono ${userGotTitle ? 'text-emerald-400' : 'text-slate-400'}`}>
                  {userGotTitle ? '1/1' : '0/1'}
                </span>
              </div>
              <span className="text-[10px] font-medium">
                {userGotTitle ? 'Trouvé !' : 'Non trouvé'}
              </span>
              {!isSolo && otherGotTitle && (
                <span className="text-[10px] text-amber-400/90 font-semibold mt-0.5 text-center truncate max-w-full">
                  ({titleFinderName} 1/1)
                </span>
              )}
            </div>

            {/* Artiste */}
            <div
              className={`p-2.5 rounded-xl border flex flex-col items-center justify-center transition-all ${
                userGotArtist
                  ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300 shadow-sm shadow-emerald-500/10'
                  : 'bg-dark-900 border-slate-800 text-slate-400'
              }`}
            >
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Artiste</span>
              <div className="flex items-center space-x-1.5 my-0.5">
                {userGotArtist ? (
                  <Check className="w-4 h-4 text-emerald-400 stroke-[3]" />
                ) : (
                  <X className="w-4 h-4 text-rose-400 stroke-[3]" />
                )}
                <span className={`text-base font-black font-mono ${userGotArtist ? 'text-emerald-400' : 'text-slate-400'}`}>
                  {userGotArtist ? '1/1' : '0/1'}
                </span>
              </div>
              <span className="text-[10px] font-medium">
                {userGotArtist ? 'Trouvé !' : 'Non trouvé'}
              </span>
              {!isSolo && otherGotArtist && (
                <span className="text-[10px] text-amber-400/90 font-semibold mt-0.5 text-center truncate max-w-full">
                  ({artistFinderName} 1/1)
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Réponses fausses des adversaires sur cette manche */}
        {!isSolo && opponentWrongGuesses.length > 0 && (
          <div className="w-full bg-rose-500/10 border border-rose-500/30 rounded-2xl p-3 mb-4 text-left">
            <div className="flex items-center space-x-1.5 mb-2 text-rose-400">
              <X className="w-3.5 h-3.5 stroke-[2.5]" />
              <span className="text-[10px] font-black uppercase tracking-wider">
                {opponentWrongGuesses.length > 1 ? 'Réponses erronées des adversaires' : 'Réponse erronée'}
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {opponentWrongGuesses.map((item, idx) => (
                <span
                  key={idx}
                  className="inline-flex items-center space-x-1.5 bg-dark-900/90 border border-rose-500/30 text-rose-300 text-xs px-2.5 py-1 rounded-xl font-medium"
                >
                  <span className="text-slate-400 text-[10px]">{item.playerName} :</span>
                  <span className="line-through font-bold text-rose-400">« {item.guess} »</span>
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Tentatives erronées en solo */}
        {isSolo && myWrongGuesses.length > 0 && (
          <div className="w-full bg-rose-500/10 border border-rose-500/30 rounded-2xl p-3 mb-4 text-left">
            <div className="flex items-center space-x-1.5 mb-2 text-rose-400">
              <X className="w-3.5 h-3.5 stroke-[2.5]" />
              <span className="text-[10px] font-black uppercase tracking-wider">
                Vos tentatives erronées
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {myWrongGuesses.map((item, idx) => (
                <span
                  key={idx}
                  className="inline-flex items-center space-x-1.5 bg-dark-900/90 border border-rose-500/30 text-rose-300 text-xs px-2.5 py-1 rounded-xl font-medium"
                >
                  <span className="line-through font-bold text-rose-400">« {item.guess} »</span>
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Scores d'équipe si teamMode */}
        {isCustom && teamMode === 'TEAMS' && (
          <div className="w-full bg-dark-950/80 border border-slate-800 rounded-2xl p-2.5 mb-3 flex items-center justify-around text-xs">
            <div className="flex items-center space-x-1.5">
              <span>🔵</span>
              <span className="font-bold text-blue-400">Bleus :</span>
              <span className="font-mono font-black text-white">{teamScores['BLUE'] ?? 0} pts</span>
            </div>
            <span className="text-slate-600 font-bold">|</span>
            <div className="flex items-center space-x-1.5">
              <span>🔴</span>
              <span className="font-bold text-rose-400">Rouges :</span>
              <span className="font-mono font-black text-white">{teamScores['RED'] ?? 0} pts</span>
            </div>
          </div>
        )}

        {/* Mini-classement multijoueur si custom */}
        {isCustom && leaderboard && leaderboard.length > 0 && (
          <div className="w-full bg-dark-950/60 border border-slate-800 rounded-2xl p-2.5 mb-4">
            <div className="flex items-center justify-between mb-1.5 px-1">
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400 flex items-center space-x-1">
                <Users className="w-3 h-3" />
                <span>Scores individuels</span>
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              {leaderboard.map((p, idx) => {
                const pTeam = playerTeams[p.playerId];
                return (
                  <div
                    key={p.playerId}
                    className={`text-[11px] px-2.5 py-1 rounded-lg border flex items-center space-x-1.5 ${
                      user && p.playerId === user.id
                        ? 'bg-brand-500/20 border-brand-500/50 text-white font-bold'
                        : 'bg-dark-900 border-slate-800 text-slate-300'
                    }`}
                  >
                    {teamMode === 'TEAMS' && (
                      <span className="text-[10px]">{pTeam === 'RED' ? '🔴' : '🔵'}</span>
                    )}
                    <span>{idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : `#${idx + 1}`}</span>
                    <span className="truncate max-w-[80px]">{p.playerName}</span>
                    <span className="font-mono font-black text-brand-400">{p.score} pts</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Barre de compte à rebours visuelle */}
        <div className="w-full h-1.5 bg-dark-950 rounded-full overflow-hidden mb-4 border border-slate-800">
          <div
            className="h-full bg-brand-500 transition-all duration-1000 ease-linear"
            style={{ width: `${(secondsRemaining / 5) * 100}%` }}
          />
        </div>

        {/* En solo : bouton de passage manuel avec raccourci Espace. En multijoueur : décompte automatique sans possibilité de passer */}
        {isSolo ? (
          <button
            onClick={handleNext}
            className="w-full bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 text-white py-3.5 px-6 rounded-2xl font-bold flex items-center justify-center space-x-2 shadow-lg shadow-brand-600/30 transition-transform active:scale-98 cursor-pointer group"
          >
            {isLastRound ? (
              <>
                <Trophy className="w-5 h-5 text-amber-300" />
                <span>Voir les résultats finaux</span>
                <kbd className="ml-2 text-[10px] font-mono uppercase bg-black/40 border border-white/20 px-2 py-0.5 rounded-lg text-white/90">
                  Espace
                </kbd>
              </>
            ) : (
              <>
                <span>Passer à la suite ({secondsRemaining}s)</span>
                <kbd className="ml-2 text-[10px] font-mono uppercase bg-black/40 border border-white/20 px-2 py-0.5 rounded-lg text-white/90 group-hover:bg-black/50">
                  Espace
                </kbd>
                <ArrowRight className="w-4 h-4 ml-1" />
              </>
            )}
          </button>
        ) : (
          <div className="w-full bg-dark-950/80 border border-slate-800 text-slate-300 py-3.5 px-6 rounded-2xl font-bold flex items-center justify-center space-x-2.5 shadow-lg select-none">
            <Clock className="w-4 h-4 text-brand-400 animate-spin" style={{ animationDuration: '3s' }} />
            <span className="text-sm font-semibold">
              {isLastRound
                ? `Résultats finaux dans ${secondsRemaining}s...`
                : `Manche suivante dans ${secondsRemaining}s...`}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
