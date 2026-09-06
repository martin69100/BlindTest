import React, { useState, useEffect } from 'react';
import { Radar, X, Sparkles } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { matchmakingService } from '../services/api';

interface Props {
  themeName: string;
  onCancel: () => void;
}

export const MatchmakingRadarScreen: React.FC<Props> = ({ themeName, onCancel }) => {
  const { user } = useAuthStore();
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setElapsedSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // Formule d'élargissement : delta = min(50 + floor(t/3)*25, 300)
  const currentDelta = Math.min(50 + Math.floor(elapsedSeconds / 3) * 25, 300);
  const userElo = user?.elo || 1000;
  const minElo = Math.max(100, userElo - currentDelta);
  const maxElo = userElo + currentDelta;

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] p-6 text-center animate-in fade-in duration-300">
      {/* Radar Cercle Visuel */}
      <div className="relative w-64 h-64 sm:w-80 sm:h-80 flex items-center justify-center mb-8">
        {/* Cercles concentriques */}
        <div className="absolute inset-0 rounded-full border border-brand-500/20 animate-ping opacity-30 duration-1000" />
        <div className="absolute inset-4 rounded-full border border-brand-500/30" />
        <div className="absolute inset-16 rounded-full border border-brand-500/40" />
        <div className="absolute inset-28 rounded-full border border-brand-500/50" />

        {/* Faisceau radar tournant */}
        <div className="absolute inset-0 rounded-full bg-gradient-to-tr from-brand-500/20 via-transparent to-transparent animate-radar-sweep pointer-events-none" />

        {/* Centre du radar */}
        <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-brand-600 to-indigo-600 flex items-center justify-center shadow-xl shadow-brand-500/50 z-10">
          <Radar className="w-8 h-8 text-white animate-spin" style={{ animationDuration: '6s' }} />
        </div>
      </div>

      <h2 className="text-2xl font-black text-white tracking-tight mb-2 flex items-center justify-center space-x-2">
        <span>Recherche d'un adversaire...</span>
        <Sparkles className="w-5 h-5 text-amber-400" />
      </h2>

      <p className="text-sm text-slate-400 mb-6">
        Thème sélectionné : <span className="text-brand-400 font-bold">{themeName}</span>
      </p>

      {/* Box Plage ELO dynamique */}
      <div className="bg-dark-900 border border-slate-800 rounded-2xl p-4 max-w-sm w-full mb-8 shadow-inner">
        <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
          <span>Temps d'attente</span>
          <span className="font-mono font-bold text-white">{elapsedSeconds}s</span>
        </div>
        <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
          <span>Plage de recherche ELO</span>
          <span className="font-bold text-brand-400">±{currentDelta} pts</span>
        </div>
        <div className="flex items-center justify-between text-xs text-slate-400">
          <span>Cibles admissibles</span>
          <span className="font-mono font-semibold text-slate-200">[{minElo} - {maxElo}]</span>
        </div>
      </div>

      {/* Bouton Annuler */}
      <button
        onClick={async () => {
          if (user) await matchmakingService.leaveQueue(user.id);
          onCancel();
        }}
        className="flex items-center space-x-2 text-xs font-bold text-rose-400 hover:text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 px-6 py-2.5 rounded-full transition-colors cursor-pointer"
      >
        <X className="w-4 h-4" />
        <span>Annuler la recherche</span>
      </button>
    </div>
  );
};
