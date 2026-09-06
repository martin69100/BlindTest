import React, { useEffect, useState } from 'react';
import { ArrowRight, Trophy, Disc, Clock } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { wsService } from '../services/websocket';

export const RoundRevealCard: React.FC = () => {
  const { gameId, phase, revealedTrack, roundNumber, totalRounds, isLastRound, isSolo } = useGameStore();
  const [secondsRemaining, setSecondsRemaining] = useState(5);

  const handleNext = () => {
    if (!gameId) return;
    wsService.sendNextRound(gameId);
  };

  // Compte à rebours automatique de 5 secondes pour enchaîner
  useEffect(() => {
    if (phase !== 'REVEAL') return;

    setSecondsRemaining(5);
    const interval = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          handleNext();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [phase, roundNumber]);

  if (phase !== 'REVEAL' || !revealedTrack) return null;

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
            className="w-44 h-44 rounded-2xl shadow-xl border border-slate-700 object-cover mb-4"
          />
        ) : (
          <div className="w-44 h-44 rounded-2xl bg-dark-800 border border-slate-700 flex items-center justify-center mb-4 text-slate-500">
            <Disc className="w-16 h-16 animate-spin" />
          </div>
        )}

        <h3 className="text-xl font-black text-white text-center tracking-tight mb-1">
          {revealedTrack.title}
        </h3>
        <p className="text-sm font-semibold text-brand-400 text-center mb-2">
          {revealedTrack.artist}
        </p>
        {revealedTrack.albumName && (
          <p className="text-xs text-slate-400 italic text-center mb-5">
            Album : {revealedTrack.albumName}
          </p>
        )}

        {/* Barre de compte à rebours visuelle */}
        <div className="w-full h-1.5 bg-dark-950 rounded-full overflow-hidden mb-4 border border-slate-800">
          <div
            className="h-full bg-brand-500 transition-all duration-1000 ease-linear"
            style={{ width: `${(secondsRemaining / 5) * 100}%` }}
          />
        </div>

        <button
          onClick={handleNext}
          disabled={!isSolo && !isLastRound}
          className={`w-full bg-gradient-to-r from-brand-600 to-indigo-600 text-white py-3 px-6 rounded-xl font-bold flex items-center justify-center space-x-2 shadow-lg transition-transform ${
            isSolo || isLastRound
              ? 'hover:from-brand-500 hover:to-indigo-500 cursor-pointer active:scale-98 shadow-brand-600/30'
              : 'opacity-90 cursor-default'
          }`}
        >
          {isLastRound ? (
            <>
              <Trophy className="w-5 h-5 text-amber-300" />
              <span>Voir les résultats finaux</span>
            </>
          ) : (
            <>
              <span>{isSolo ? `Passer à la suite (${secondsRemaining}s)` : `Manche suivante dans ${secondsRemaining}s`}</span>
              <ArrowRight className="w-5 h-5" />
            </>
          )}
        </button>
      </div>
    </div>
  );
};
