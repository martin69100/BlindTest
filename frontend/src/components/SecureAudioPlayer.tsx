import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Volume2, VolumeX, AlertCircle, PlayCircle } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';

export const SecureAudioPlayer: React.FC = () => {
  const { audioUrl, phase, gameId } = useGameStore();
  const { user } = useAuthStore();
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Récupération sécurisée et persistante du volume (défaut 0.8)
  const [isMuted, setIsMuted] = useState<boolean>(() => {
    return localStorage.getItem('blindtest_muted') === 'true';
  });

  const [volume, setVolume] = useState<number>(() => {
    const saved = localStorage.getItem('blindtest_volume');
    if (!saved) return 0.8;
    const parsed = parseFloat(saved);
    return !isNaN(parsed) && parsed >= 0 && parsed <= 1 ? parsed : 0.8;
  });

  const [autoplayBlocked, setAutoplayBlocked] = useState<boolean>(false);
  const [audioError, setAudioError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  const isMutedRef = useRef(isMuted);
  isMutedRef.current = isMuted;

  const volumeRef = useRef(volume);
  volumeRef.current = volume;

  // Fonction utilitaire pour lancer la lecture avec gestion d'autoplay et attente si la source charge encore
  const tryPlay = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !audio.src) return;

    audio.volume = isMutedRef.current ? 0 : volumeRef.current;
    audio.muted = isMutedRef.current;

    const playPromise = audio.play();
    if (playPromise !== undefined) {
      playPromise
        .then(() => {
          setAutoplayBlocked(false);
          setAudioError(null);
        })
        .catch((err) => {
          if (err.name === 'NotAllowedError') {
            setAutoplayBlocked(true);
            console.warn("Autoplay bloqué par la politique du navigateur. Interaction requise.");
          } else if (err.name === 'NotSupportedError') {
            // La source est en cours de résolution par le navigateur : relancer dès que canplay est prêt
            const onCanPlay = () => {
              audio.removeEventListener('canplay', onCanPlay);
              if (phase === 'PLAYING') {
                audio.play().catch(() => {});
              }
            };
            audio.addEventListener('canplay', onCanPlay, { once: true });
          } else if (err.name !== 'AbortError') {
            console.warn("Lecture audio en attente de source :", err.name);
          }
        });
    }
  }, [phase]);

  const handleManualRetry = () => {
    setAudioError(null);
    setRetryCount(0);
    // Demander au serveur un nouveau lien signé tout frais
    if (gameId && user) {
      wsService.sendReady(gameId, user.id);
    }
    if (audioRef.current) {
      audioRef.current.load();
      tryPlay();
    }
  };

  // Déblocage automatique au moindre clic ou appui clavier dans la fenêtre
  useEffect(() => {
    const handleUserInteraction = () => {
      if (autoplayBlocked && audioRef.current && phase === 'PLAYING') {
        tryPlay();
      }
    };

    window.addEventListener('click', handleUserInteraction);
    window.addEventListener('keydown', handleUserInteraction);

    return () => {
      window.removeEventListener('click', handleUserInteraction);
      window.removeEventListener('keydown', handleUserInteraction);
    };
  }, [autoplayBlocked, phase, tryPlay]);

  // Réaction STRICTEMENT au changement d'URL audio (nouvelle manche)
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    if (audioUrl) {
      setAudioError(null);
      setAutoplayBlocked(false);
      setRetryCount(0);
      audio.currentTime = 0;

      if (phase === 'PLAYING') {
        tryPlay();
      }
    } else {
      audio.pause();
    }
  }, [audioUrl, phase, tryPlay]);

  // Réaction au changement de phase de jeu (Buzz, Vol de main, Révélation)
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    if (phase === 'PLAYING') {
      tryPlay();
    } else {
      audio.pause();
    }
  }, [phase, tryPlay]);

  // Réaction au changement de volume ou mute : met à jour le volume directement sans recharger l'audio
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = isMuted ? 0 : volume;
    audio.muted = isMuted;
  }, [volume, isMuted]);

  const handleVolumeChange = (newVolume: number) => {
    setVolume(newVolume);
    localStorage.setItem('blindtest_volume', newVolume.toString());
    if (isMuted && newVolume > 0) {
      setIsMuted(false);
      localStorage.setItem('blindtest_muted', 'false');
    }
  };

  const handleToggleMute = () => {
    const nextMuted = !isMuted;
    setIsMuted(nextMuted);
    localStorage.setItem('blindtest_muted', nextMuted.toString());
  };

  return (
    <div className="flex items-center space-x-2.5 bg-slate-900/50 hover:bg-slate-900/80 border border-slate-800/80 hover:border-slate-700/80 px-3 py-1.5 rounded-xl backdrop-blur-md transition-colors shadow-inner">
      {/* Élément audio HTML5 persistant */}
      <audio
        ref={audioRef}
        src={audioUrl || undefined}
        preload="auto"
        crossOrigin="anonymous"
        onPlaying={() => {
          setAutoplayBlocked(false);
          setAudioError(null);
        }}
        onError={(e) => {
          const err = (e.currentTarget as HTMLAudioElement).error;
          console.warn("Erreur de flux audio :", err, "URL :", audioUrl);
          if (audioUrl) {
            if (retryCount < 2) {
              setRetryCount((prev) => prev + 1);
              // Si le flux a échoué (ex: token CDN expiré), demander une resynchronisation avec un token frais
              if (gameId && user) {
                wsService.sendReady(gameId, user.id);
              }
              setTimeout(() => {
                if (audioRef.current && phase === 'PLAYING') {
                  tryPlay();
                }
              }, 400);
            } else {
              setAudioError("Flux audio temporairement indisponible");
            }
          }
        }}
      />

      {/* Alerte Autoplay bloqué : bouton cliquable bien visible */}
      {autoplayBlocked && phase === 'PLAYING' && (
        <button
          onClick={tryPlay}
          className="flex items-center space-x-1.5 bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 text-[11px] font-bold px-2.5 py-1 rounded-lg animate-pulse transition-all cursor-pointer shadow-sm shadow-amber-500/10"
          title="Cliquez pour débloquer le son"
        >
          <PlayCircle className="w-3.5 h-3.5 text-amber-400" />
          <span>Activer le son</span>
        </button>
      )}

      {/* Erreur de lecture avec action de rechargement */}
      {audioError && !autoplayBlocked && (
        <button
          onClick={handleManualRetry}
          className="flex items-center space-x-1 text-rose-400 hover:text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 px-2 py-0.5 rounded-lg border border-rose-500/30 text-[10px] font-semibold cursor-pointer transition-colors"
          title="Cliquez pour réessayer la lecture du flux audio"
        >
          <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
          <span className="hidden sm:inline">Réessayer son</span>
        </button>
      )}

      {/* Visualiseur d'ondes compact animé */}
      <div className="flex items-center space-x-0.5 h-4 px-1" title="Indicateur de lecture">
        {[1, 2, 3, 4, 5].map((i) => (
          <div
            key={i}
            className={`w-0.5 bg-gradient-to-t from-brand-500 to-indigo-400 rounded-full transition-all duration-300 ${
              phase === 'PLAYING' && !autoplayBlocked && !isMuted
                ? 'animate-pulse h-3.5'
                : 'h-1 opacity-20'
            }`}
            style={{ animationDelay: `${i * 120}ms` }}
          />
        ))}
      </div>

      {/* Bouton Mute / Unmute */}
      <button
        onClick={handleToggleMute}
        title={isMuted ? 'Rétablir le son' : 'Couper le son'}
        className="p-1 text-slate-400 hover:text-white rounded-lg transition-colors cursor-pointer"
      >
        {isMuted ? <VolumeX className="w-3.5 h-3.5 text-rose-400" /> : <Volume2 className="w-3.5 h-3.5 text-slate-300" />}
      </button>

      {/* Slider Volume élégant */}
      <div className="flex items-center space-x-1.5 group">
        <input
          type="range"
          min="0"
          max="1"
          step="0.05"
          value={isMuted ? 0 : volume}
          onChange={(e) => handleVolumeChange(parseFloat(e.target.value))}
          onMouseUp={(e) => (e.target as HTMLElement).blur()}
          onTouchEnd={(e) => (e.target as HTMLElement).blur()}
          onKeyUp={(e) => (e.target as HTMLElement).blur()}
          className="w-16 sm:w-20 h-1 bg-slate-800 group-hover:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-brand-500 transition-colors"
          title={`Volume : ${Math.round(volume * 100)}%`}
        />
        <span className="text-[10px] font-mono font-semibold text-slate-400 w-6 text-right select-none">
          {isMuted ? '0%' : `${Math.round(volume * 100)}%`}
        </span>
      </div>
    </div>
  );
};
