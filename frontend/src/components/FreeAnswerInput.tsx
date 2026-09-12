import React, { useState, useEffect, useRef } from 'react';
import { Send, CheckCircle2, Circle, Sparkles, Music2, UserCheck } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';

export const FreeAnswerInput: React.FC = () => {
  const {
    gameId,
    myFoundTitle,
    myFoundArtist,
    freeFeedEvents,
    lastWrongGuess,
    teamMode,
  } = useGameStore();
  const { user } = useAuthStore();

  const [guess, setGuess] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showErrorShake, setShowErrorShake] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const bothFound = myFoundTitle && myFoundArtist;

  // Focus automatique au montage et à chaque tour
  useEffect(() => {
    if (!bothFound) {
      inputRef.current?.focus();
    }
  }, [bothFound]);

  // Déclencher une secousse si la dernière erreur vient de nous
  useEffect(() => {
    if (lastWrongGuess && user && lastWrongGuess.playerId === user.id) {
      setShowErrorShake(true);
      const timer = setTimeout(() => setShowErrorShake(false), 500);
      return () => clearTimeout(timer);
    }
  }, [lastWrongGuess, user]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanGuess = guess.trim();
    if (!cleanGuess || !gameId || !user || bothFound || isSubmitting) return;

    setIsSubmitting(true);
    wsService.sendAnswer(gameId, user.id, cleanGuess);
    setGuess('');
    setTimeout(() => {
      setIsSubmitting(false);
      inputRef.current?.focus();
    }, 150);
  };

  return (
    <div className="w-full max-w-xl mx-auto flex flex-col items-center space-y-4 animate-in fade-in duration-300">
      {/* Badges Titre & Artiste pour le joueur */}
      <div className="w-full grid grid-cols-2 gap-3">
        {/* Statut Titre */}
        <div
          className={`p-3.5 rounded-2xl border flex items-center space-x-3 transition-all ${
            myFoundTitle
              ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300 shadow-md shadow-emerald-500/10'
              : 'bg-dark-900/80 border-slate-800 text-slate-400'
          }`}
        >
          {myFoundTitle ? (
            <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0 animate-bounce" />
          ) : (
            <Circle className="w-6 h-6 text-slate-600 shrink-0" />
          )}
          <div className="min-w-0">
            <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Titre (1 pt)</p>
            <p className="text-xs sm:text-sm font-extrabold truncate">
              {myFoundTitle ? '✓ Trouvé !' : 'À deviner...'}
            </p>
          </div>
        </div>

        {/* Statut Artiste */}
        <div
          className={`p-3.5 rounded-2xl border flex items-center space-x-3 transition-all ${
            myFoundArtist
              ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300 shadow-md shadow-emerald-500/10'
              : 'bg-dark-900/80 border-slate-800 text-slate-400'
          }`}
        >
          {myFoundArtist ? (
            <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0 animate-bounce" />
          ) : (
            <Circle className="w-6 h-6 text-slate-600 shrink-0" />
          )}
          <div className="min-w-0">
            <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Artiste (1 pt)</p>
            <p className="text-xs sm:text-sm font-extrabold truncate">
              {myFoundArtist ? '✓ Trouvé !' : 'À deviner...'}
            </p>
          </div>
        </div>
      </div>

      {/* Formulaire de saisie ou message de succès complet */}
      {bothFound ? (
        <div className="w-full bg-gradient-to-r from-emerald-500/20 via-brand-500/20 to-teal-500/20 border border-emerald-500/40 rounded-3xl p-5 text-center shadow-xl">
          <Sparkles className="w-8 h-8 text-emerald-400 mx-auto mb-2 animate-spin" />
          <h4 className="text-base font-black text-white">Bravo ! Vous avez trouvé les 2 réponses !</h4>
          <p className="text-xs text-emerald-300/80 mt-1 font-semibold">
            +2 points remportés pour cette manche. En attente de la fin du morceau...
          </p>
        </div>
      ) : (
        <form
          onSubmit={handleSubmit}
          className={`w-full flex items-center gap-2 bg-dark-900/90 border rounded-2xl p-2 shadow-2xl transition-all ${
            showErrorShake
              ? 'border-rose-500 animate-shake shadow-rose-500/20'
              : 'border-slate-800 focus-within:border-brand-500'
          }`}
        >
          <div className="pl-3 text-slate-500">
            <Music2 className="w-5 h-5" />
          </div>
          <input
            ref={inputRef}
            type="text"
            value={guess}
            onChange={(e) => setGuess(e.target.value)}
            disabled={bothFound}
            placeholder="Tapez le titre ou l'artiste puis appuyez sur Entrée..."
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
            className="flex-1 bg-transparent text-white placeholder-slate-500 font-semibold px-2 py-3 text-sm sm:text-base outline-none disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={!guess.trim() || bothFound || isSubmitting}
            className="bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 disabled:opacity-40 text-white font-black px-5 py-3 rounded-xl flex items-center space-x-1.5 shadow-lg shadow-brand-500/25 active:scale-95 transition-all text-xs cursor-pointer"
          >
            <span>Envoyer</span>
            <Send className="w-3.5 h-3.5" />
          </button>
        </form>
      )}

      {/* Fil d'actualité en direct des réponses trouvées dans le salon */}
      {freeFeedEvents && freeFeedEvents.length > 0 && (
        <div className="w-full bg-dark-950/60 border border-slate-800/80 rounded-2xl p-3 max-h-36 overflow-y-auto space-y-1.5 shadow-inner">
          <div className="flex items-center space-x-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1 px-1">
            <UserCheck className="w-3 h-3 text-brand-400" />
            <span>Réponses trouvées pendant ce morceau :</span>
          </div>
          {freeFeedEvents.map((item) => {
            const isBlue = item.playerTeam === 'BLUE';
            const isRed = item.playerTeam === 'RED';
            return (
              <div
                key={item.id}
                className="flex items-center justify-between text-xs py-1 px-2.5 rounded-lg bg-dark-900/80 border border-slate-800/60 animate-in fade-in slide-in-from-bottom-1 duration-200"
              >
                <div className="flex items-center space-x-1.5 truncate">
                  {teamMode === 'TEAMS' && (
                    <span className="text-xs">{isRed ? '🔴' : '🔵'}</span>
                  )}
                  <span className={`font-black truncate ${isBlue ? 'text-blue-400' : isRed ? 'text-rose-400' : 'text-slate-200'}`}>
                    {item.playerName}
                  </span>
                  <span className="text-slate-400 font-medium">a trouvé</span>
                  <span className="font-extrabold text-emerald-400 uppercase text-[10px] bg-emerald-500/10 border border-emerald-500/20 px-1.5 py-0.2 rounded">
                    {item.foundType === 'TITLE' ? 'le Titre' : "l'Artiste"}
                  </span>
                </div>
                <span className="text-[10px] font-extrabold font-mono text-emerald-400 shrink-0 ml-2">
                  +1 pt
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
