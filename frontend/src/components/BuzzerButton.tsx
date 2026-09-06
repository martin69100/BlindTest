import React, { useEffect, useCallback } from 'react';
import { Zap } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';

export const BuzzerButton: React.FC = () => {
  const { gameId, phase, buzzerPlayerId } = useGameStore();
  const { user } = useAuthStore();

  const isMyBuzz = user && buzzerPlayerId === user.id;
  const isSomeoneElseBuzz = buzzerPlayerId && (!user || buzzerPlayerId !== user.id);
  const canBuzz = phase === 'PLAYING';

  const triggerBuzz = useCallback(() => {
    if (!canBuzz || !gameId || !user) return;
    wsService.sendBuzz(gameId, user.id);
  }, [canBuzz, gameId, user]);

  // Écoute clavier (Touche Entrée ou Espace)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ne pas déclencher si l'utilisateur est déjà en train de taper dans un champ de texte
      if (['INPUT', 'TEXTAREA'].includes((e.target as HTMLElement).tagName)) {
        return;
      }
      if (e.code === 'Enter' || e.code === 'Space') {
        e.preventDefault();
        triggerBuzz();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [triggerBuzz]);

  return (
    <div className="flex flex-col items-center justify-center p-4">
      <button
        onClick={triggerBuzz}
        disabled={!canBuzz}
        className={`relative group rounded-full p-8 transition-all duration-300 transform active:scale-95 ${
          canBuzz
            ? 'bg-gradient-to-b from-rose-500 to-rose-600 hover:from-rose-400 hover:to-rose-500 text-white shadow-2xl shadow-rose-500/50 cursor-pointer animate-pulse hover:scale-105'
            : isMyBuzz
            ? 'bg-emerald-500/20 border-2 border-emerald-500 text-emerald-400 cursor-default'
            : isSomeoneElseBuzz
            ? 'bg-dark-800 border-2 border-slate-700 text-slate-500 cursor-not-allowed opacity-60'
            : 'bg-dark-800 text-slate-600 cursor-not-allowed opacity-50'
        }`}
      >
        <div className="w-24 h-24 sm:w-32 sm:h-32 rounded-full flex flex-col items-center justify-center border-4 border-white/20">
          <Zap className={`w-10 h-10 sm:w-12 sm:h-12 ${canBuzz ? 'animate-bounce' : ''}`} />
          <span className="mt-2 text-xs sm:text-sm font-black tracking-widest uppercase">
            {isMyBuzz ? 'À VOUS !' : isSomeoneElseBuzz ? 'BLOQUÉ' : 'BUZZ !'}
          </span>
        </div>
      </button>

      <p className="mt-3 text-xs text-slate-400 font-medium tracking-wide">
        Appuyez sur <kbd className="px-2 py-0.5 bg-dark-800 border border-slate-700 rounded text-slate-200 font-mono text-[11px]">Entrée</kbd> ou <kbd className="px-2 py-0.5 bg-dark-800 border border-slate-700 rounded text-slate-200 font-mono text-[11px]">Espace</kbd>
      </p>
    </div>
  );
};
