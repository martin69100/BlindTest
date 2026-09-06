import React, { useState, useEffect, useRef } from 'react';
import { Send, Clock, Sparkles, SkipForward } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';

export const AnswerInput: React.FC = () => {
  const {
    gameId,
    phase,
    buzzerPlayerId,
    buzzerName,
    firstFoundType,
    firstFoundName,
    inputTimeoutSeconds,
    bonusDurationSeconds,
  } = useGameStore();
  const { user } = useAuthStore();

  const [guess, setGuess] = useState('');
  const [timeLeft, setTimeLeft] = useState(5);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const isMyTurn = user && buzzerPlayerId === user.id;
  const isBonus = phase === 'BONUS';

  // Réinitialisation et focus automatique dès l'apparition du champ
  useEffect(() => {
    setGuess('');
    if (phase === 'BUZZED') {
      setTimeLeft(inputTimeoutSeconds || 5);
      setTimeout(() => inputRef.current?.focus(), 50);
    } else if (phase === 'BONUS') {
      setTimeLeft(bonusDurationSeconds || 10);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [phase, inputTimeoutSeconds, bonusDurationSeconds]);

  // Décompte du timer
  useEffect(() => {
    if (phase !== 'BUZZED' && phase !== 'BONUS') return;

    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [phase]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!gameId || !user || !guess.trim()) return;

    if (isBonus) {
      wsService.sendBonus(gameId, user.id, guess.trim());
    } else {
      wsService.sendAnswer(gameId, user.id, guess.trim());
    }
    setGuess('');
  };

  if (phase !== 'BUZZED' && phase !== 'BONUS') return null;

  return (
    <div className="w-full max-w-lg mx-auto bg-dark-900 border border-brand-500/40 rounded-2xl p-6 shadow-2xl shadow-brand-500/10 backdrop-blur-xl animate-in fade-in zoom-in-95 duration-200">
      {/* Header statut */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          {isBonus ? (
            <span className="flex items-center text-xs font-bold text-amber-400 bg-amber-400/10 px-3 py-1 rounded-full border border-amber-400/30">
              <Sparkles className="w-3.5 h-3.5 mr-1.5 animate-spin" />
              BONUS (+1 PT) : Trouvez {firstFoundType === 'TITLE' ? "l'ARTISTE" : 'le TITRE'} !
            </span>
          ) : (
            <span className="text-xs font-bold text-rose-400 bg-rose-400/10 px-3 py-1 rounded-full border border-rose-400/30">
              {isMyTurn
                ? firstFoundType
                  ? `À votre tour (Vol de main) ! Trouvez ${firstFoundType === 'TITLE' ? "l'ARTISTE" : 'le TITRE'} !`
                  : 'À votre tour ! Entrez le Titre OU l’Artiste'
                : firstFoundType
                ? `${buzzerName} tente de trouver ${firstFoundType === 'TITLE' ? "l'Artiste" : 'le Titre'} (Vol de main)...`
                : `${buzzerName} est en train de répondre...`}
            </span>
          )}
        </div>

        {/* Chrono */}
        <div className="flex items-center space-x-1.5 text-xs font-mono font-bold text-slate-300 bg-dark-800 px-3 py-1 rounded-lg border border-slate-700">
          <Clock className={`w-3.5 h-3.5 ${timeLeft <= 2 ? 'text-rose-400 animate-bounce' : 'text-slate-400'}`} />
          <span>{timeLeft}s</span>
        </div>
      </div>

      {firstFoundName && (
        <p className="text-xs text-slate-400 mb-3">
          Déjà validé : <span className="text-emerald-400 font-semibold">{firstFoundName}</span> ({firstFoundType === 'TITLE' ? 'Titre' : 'Artiste'})
        </p>
      )}

      {/* Formulaire de saisie si c'est notre tour */}
      {isMyTurn ? (
        <form onSubmit={handleSubmit} className="flex space-x-2">
          <input
            ref={inputRef}
            type="text"
            value={guess}
            onChange={(e) => setGuess(e.target.value)}
            placeholder={
              firstFoundType
                ? (firstFoundType === 'TITLE' ? "Nom de l'artiste..." : 'Titre du morceau...')
                : 'Tapez le titre OU le nom de l’artiste...'
            }
            className="flex-1 bg-dark-950 border border-brand-500/50 rounded-xl px-4 py-3 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition-all"
          />
          <button
            type="submit"
            disabled={!guess.trim()}
            className="bg-brand-600 hover:bg-brand-500 disabled:opacity-40 disabled:cursor-not-allowed text-white px-5 rounded-xl font-bold text-sm flex items-center justify-center transition-colors shadow-lg shadow-brand-600/30"
          >
            <Send className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={() => {
              if (gameId && user) {
                wsService.sendSkipRound(gameId, user.id);
              }
            }}
            className="bg-dark-800 hover:bg-dark-700 text-amber-400 border border-amber-500/30 px-3.5 rounded-xl font-bold text-xs flex items-center justify-center space-x-1 transition-colors cursor-pointer"
            title="Passer ce morceau (0 point) et afficher la solution"
          >
            <SkipForward className="w-3.5 h-3.5" />
            <span>Passer</span>
          </button>
        </form>
      ) : (
        <div className="py-4 text-center">
          <p className="text-sm text-slate-400 animate-pulse font-medium">
            {isBonus
              ? `${buzzerName} tente le bonus (+1 PT)... Soyez prêt à voler la main s'il échoue !`
              : firstFoundType
              ? `${buzzerName} tente le point restant (Vol de main)...`
              : "L'adversaire a la main... Soyez prêt si la réponse échoue !"}
          </p>
        </div>
      )}
    </div>
  );
};
