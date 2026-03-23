import {useState, useEffect, useRef} from "react";
import {DigitalTwin, InferenceEngine, RetrainingConfig, useApp} from "../../context/AppContext";
import {
    Cpu, CheckCircle2, AlertCircle, Save, Check,
    BarChart3, Zap, Settings2, Play, RefreshCw, Clock,
    ChevronDown, ToggleLeft, ToggleRight, Calendar, Info,
} from "lucide-react";

// ─── Config form ──────────────────────────────────────────────────────────────

const ALGORITHMS = ["LSTM", "GRU", "Transformer", "TCN", "MLP"];

interface ConfigFormProps {
    engine: InferenceEngine | null;
    onSave: (engine: InferenceEngine) => void;
}

function ConfigForm({engine, onSave}: ConfigFormProps) {
    const [algorithm, setAlgorithm] = useState(engine?.algorithm ?? "LSTM");
    const [epochs, setEpochs] = useState(String(engine?.epochs ?? 100));
    const [learningRate, setLearningRate] = useState(String(engine?.learningRate ?? 0.001));
    const [windowSize, setWindowSize] = useState(String(engine?.windowSize ?? 60));
    const [batchSize, setBatchSize] = useState(String(engine?.batchSize ?? 32));
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 700));
        onSave({
            ...(engine ?? {}),
            trained: engine?.trained ?? false,
            algorithm,
            epochs: parseInt(epochs) || 100,
            learningRate: parseFloat(learningRate) || 0.001,
            windowSize: parseInt(windowSize) || 60,
            batchSize: parseInt(batchSize) || 32,
            trainedAt: engine?.trainedAt,
            inferredVariables: engine?.inferredVariables,
            retrainingConfig: engine?.retrainingConfig,
        });
        setSaving(false);
        setSaved(true);
        setTimeout(() => setSaved(false), 2500);
    };

    const fields = [
        {label: "Epochs", value: epochs, onChange: setEpochs, hint: "Number of training iterations"},
        {label: "Learning rate", value: learningRate, onChange: setLearningRate, hint: "e.g. 0.001"},
        {label: "Window size", value: windowSize, onChange: setWindowSize, hint: "Lookback time steps"},
        {label: "Batch size", value: batchSize, onChange: setBatchSize, hint: "Samples per gradient update"},
    ];

    return (
        <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
            <div className="flex items-center gap-2 mb-5">
                <Settings2 className="w-4 h-4 text-white/40"/>
                <h3 className="text-white" style={{fontWeight: 600}}>Engine configuration</h3>
            </div>

            <div className="mb-5">
                <label className="text-white/40 text-xs uppercase tracking-wider mb-2 block">Algorithm</label>
                <div className="flex gap-2 flex-wrap">
                    {ALGORITHMS.map((a) => (
                        <button
                            key={a}
                            onClick={() => setAlgorithm(a)}
                            className={`px-3.5 py-1.5 rounded-lg border text-sm transition-all ${
                                algorithm === a
                                    ? "bg-cyan-500/20 border-cyan-500/50 text-cyan-300"
                                    : "bg-white/4 border-white/10 text-white/40 hover:border-white/20 hover:text-white/60"
                            }`}
                        >
                            {a}
                        </button>
                    ))}
                </div>
            </div>

            <div className="grid sm:grid-cols-2 gap-4 mb-5">
                {fields.map(({label, value, onChange, hint}) => (
                    <div key={label}>
                        <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 block">{label}</label>
                        <input
                            type="number"
                            value={value}
                            onChange={(e) => onChange(e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                        />
                        <p className="text-white/25 text-xs mt-1">{hint}</p>
                    </div>
                ))}
            </div>

            <button
                onClick={handleSave}
                disabled={saving}
                className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/15"
                style={{fontWeight: 500}}
            >
                {saved ? <><Check className="w-4 h-4"/> Saved</> : saving ? "Saving…" : <><Save
                    className="w-4 h-4"/> Save configuration</>}
            </button>
        </div>
    );
}

// ─── Launch training panel ────────────────────────────────────────────────────

type TrainingPhase = "idle" | "preparing" | "training" | "evaluating" | "done" | "error";

const PHASES: { phase: TrainingPhase; label: string; pct: number }[] = [
    {phase: "preparing", label: "Preparing dataset…", pct: 8},
    {phase: "training", label: "Training in progress…", pct: 75},
    {phase: "evaluating", label: "Evaluating model…", pct: 95},
    {phase: "done", label: "Training complete!", pct: 100},
];

interface LaunchPanelProps {
    twin: DigitalTwin;
    onTrainingComplete: (engine: InferenceEngine) => void;
}

function LaunchPanel({twin, onTrainingComplete}: LaunchPanelProps) {
    const [phase, setPhase] = useState<TrainingPhase>("idle");
    const [progress, setProgress] = useState(0);
    const [log, setLog] = useState<string[]>([]);
    const logRef = useRef<HTMLDivElement>(null);
    const engine = twin.inferenceEngine;

    const hasData = (twin.datasets ?? []).some((d) => d.uploadedRecords > 0);
    const hasConfig = engine !== null;

    const addLog = (msg: string) =>
        setLog((prev) => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);

    const runTraining = async () => {
        setPhase("preparing");
        setProgress(0);
        setLog([]);

        addLog("Initialising training pipeline…");
        await animateProgress(0, 8, 800);
        addLog(`Algorithm: ${engine?.algorithm ?? "LSTM"}`);
        addLog(`Epochs: ${engine?.epochs ?? 100} · LR: ${engine?.learningRate ?? 0.001}`);
        addLog(`Window: ${engine?.windowSize ?? 60} · Batch: ${engine?.batchSize ?? 32}`);

        setPhase("training");
        addLog("Starting training loop…");
        const totalEpochs = engine?.epochs ?? 100;
        let currentPct = 8;
        for (let ep = 1; ep <= Math.min(totalEpochs, 8); ep++) {
            await new Promise((r) => setTimeout(r, 350 + Math.random() * 200));
            const targetPct = 8 + (ep / 8) * 67;
            await animateProgress(currentPct, targetPct, 300);
            currentPct = targetPct;
            const loss = (0.8 / ep + 0.05 * Math.random()).toFixed(4);
            const val = (0.85 / ep + 0.04 * Math.random()).toFixed(4);
            addLog(`Epoch ${Math.round(ep * (totalEpochs / 8))}/${totalEpochs} — loss: ${loss} · val_loss: ${val}`);
        }

        setPhase("evaluating");
        addLog("Evaluating on test split…");
        await animateProgress(currentPct, 95, 600);
        await new Promise((r) => setTimeout(r, 400));

        setPhase("done");
        await animateProgress(95, 100, 300);
        addLog("✓ Training complete. Model saved.");

        // Build mock results per inferred variable
        const inferredVars = twin.subjects
            .flatMap((s) => s.variables.filter((v) => v.inferred))
            .map((v) => ({
                name: v.name,
                accuracy: +(88 + Math.random() * 8).toFixed(1),
                mae: +(0.1 + Math.random() * 1.2).toFixed(3),
                violations: +(0.5 + Math.random() * 4).toFixed(1),
            }));

        const updatedEngine: InferenceEngine = {
            ...(engine ?? {algorithm: "LSTM"}),
            trained: true,
            trainedAt: new Date().toISOString(),
            inferredVariables: inferredVars.length > 0 ? inferredVars : undefined,
        };
        onTrainingComplete(updatedEngine);
    };

    async function animateProgress(from: number, to: number, ms: number) {
        const steps = 12;
        const step = (to - from) / steps;
        const delay = ms / steps;
        for (let i = 0; i <= steps; i++) {
            setProgress(Math.min(from + step * i, 100));
            await new Promise((r) => setTimeout(r, delay));
        }
    }

    useEffect(() => {
        if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
    }, [log]);

    const isRunning = phase === "preparing" || phase === "training" || phase === "evaluating";

    const phaseInfo = PHASES.find((p) => p.phase === phase);

    return (
        <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2">
                    <Play className="w-4 h-4 text-white/40"/>
                    <h3 className="text-white" style={{fontWeight: 600}}>Launch training</h3>
                </div>
                {phase === "done" && (
                    <span
                        className="flex items-center gap-1.5 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-full">
            <CheckCircle2 className="w-3.5 h-3.5"/>
            Completed
          </span>
                )}
            </div>

            {/* Pre-flight checks */}
            <div className="flex flex-wrap gap-3 mb-5">
                {[
                    {label: "Engine configured", ok: hasConfig, hint: "Save a configuration above"},
                    {label: "Dataset available", ok: hasData, hint: "Upload data in the Data tab"},
                ].map(({label, ok, hint}) => (
                    <div
                        key={label}
                        className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs ${
                            ok
                                ? "bg-emerald-500/8 border-emerald-500/20 text-emerald-400"
                                : "bg-white/4 border-white/10 text-white/30"
                        }`}
                    >
                        {ok ? <CheckCircle2 className="w-3.5 h-3.5"/> : <AlertCircle className="w-3.5 h-3.5"/>}
                        <span style={{fontWeight: 500}}>{label}</span>
                        {!ok && <span className="text-white/20">— {hint}</span>}
                    </div>
                ))}
            </div>

            {/* Progress */}
            {phase !== "idle" && (
                <div className="mb-5">
                    <div className="flex items-center justify-between mb-1.5 text-xs">
            <span className={`${phase === "done" ? "text-emerald-400" : "text-cyan-400"}`} style={{fontWeight: 500}}>
              {phaseInfo?.label ?? "Processing…"}
            </span>
                        <span className="text-white/40 font-mono">{Math.round(progress)}%</span>
                    </div>
                    <div className="h-2 bg-white/8 rounded-full overflow-hidden">
                        <div
                            className={`h-full rounded-full transition-all duration-300 ${
                                phase === "done"
                                    ? "bg-gradient-to-r from-emerald-500 to-emerald-400"
                                    : "bg-gradient-to-r from-cyan-600 to-cyan-400"
                            }`}
                            style={{width: `${progress}%`}}
                        />
                    </div>

                    {/* Log */}
                    <div
                        ref={logRef}
                        className="mt-3 bg-black/40 border border-white/6 rounded-xl p-3 h-32 overflow-y-auto font-mono text-xs"
                    >
                        {log.map((line, i) => (
                            <div
                                key={i}
                                className={`leading-5 ${
                                    line.includes("✓") ? "text-emerald-400" :
                                        line.includes("Epoch") ? "text-cyan-300/70" : "text-white/35"
                                }`}
                            >
                                {line}
                            </div>
                        ))}
                        {isRunning && (
                            <div className="text-white/20 animate-pulse">▌</div>
                        )}
                    </div>
                </div>
            )}

            {/* Launch button */}
            <button
                onClick={runTraining}
                disabled={isRunning || !hasConfig}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm transition-all ${
                    phase === "done"
                        ? "bg-white/8 border border-white/15 text-white/50 hover:text-white/70 hover:bg-white/12"
                        : "bg-gradient-to-r from-cyan-600 to-cyan-500 hover:from-cyan-500 hover:to-cyan-400 text-white shadow-lg shadow-cyan-500/20 disabled:opacity-40 disabled:cursor-not-allowed"
                }`}
                style={{fontWeight: 500}}
            >
                {isRunning ? (
                    <>
                        <RefreshCw className="w-4 h-4 animate-spin"/>
                        Training in progress���
                    </>
                ) : phase === "done" ? (
                    <>
                        <RefreshCw className="w-4 h-4"/>
                        Retrain
                    </>
                ) : (
                    <>
                        <Play className="w-4 h-4"/>
                        Start training
                    </>
                )}
            </button>

            {!hasData && phase === "idle" && (
                <p className="text-white/25 text-xs mt-2 flex items-center gap-1.5">
                    <Info className="w-3 h-3"/>
                    Go to the Data tab to upload a training dataset first.
                </p>
            )}
        </div>
    );
}

// ─── Retraining schedule ──────────────────────────────────────────────────────

interface RetrainingPanelProps {
    config: RetrainingConfig | undefined;
    onSave: (cfg: RetrainingConfig) => void;
}

const SCHEDULES: { value: RetrainingConfig["schedule"]; label: string; desc: string }[] = [
    {value: "daily", label: "Daily", desc: "Retrain every day"},
    {value: "weekly", label: "Weekly", desc: "Retrain every Monday"},
    {value: "monthly", label: "Monthly", desc: "Retrain on the 1st"},
];

function RetrainingPanel({config, onSave}: RetrainingPanelProps) {
    const [enabled, setEnabled] = useState(config?.enabled ?? false);
    const [schedule, setSchedule] = useState<RetrainingConfig["schedule"]>(config?.schedule ?? "weekly");
    const [minRecords, setMinRecords] = useState(String(config?.minNewRecords ?? 500));
    const [timeOfDay, setTimeOfDay] = useState(config?.timeOfDay ?? "02:00");
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 600));
        onSave({enabled, schedule, minNewRecords: parseInt(minRecords) || 500, timeOfDay});
        setSaving(false);
        setSaved(true);
        setTimeout(() => setSaved(false), 2500);
    };

    return (
        <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-white/40"/>
                    <h3 className="text-white" style={{fontWeight: 600}}>Automatic retraining</h3>
                </div>
                <button
                    onClick={() => setEnabled(!enabled)}
                    className="flex items-center gap-2 transition-colors"
                >
                    {enabled ? (
                        <ToggleRight className="w-8 h-8 text-cyan-400"/>
                    ) : (
                        <ToggleLeft className="w-8 h-8 text-white/25"/>
                    )}
                    <span className={`text-xs ${enabled ? "text-cyan-400" : "text-white/30"}`}
                          style={{fontWeight: 500}}>
            {enabled ? "Enabled" : "Disabled"}
          </span>
                </button>
            </div>

            <div
                className={`flex flex-col gap-5 transition-opacity ${!enabled ? "opacity-35 pointer-events-none" : ""}`}>
                {/* Schedule */}
                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-2 block">Frequency</label>
                    <div className="grid grid-cols-3 gap-2">
                        {SCHEDULES.map(({value, label, desc}) => (
                            <button
                                key={value}
                                onClick={() => setSchedule(value)}
                                className={`flex flex-col items-start px-3 py-2.5 rounded-xl border text-left transition-all ${
                                    schedule === value
                                        ? "bg-cyan-500/15 border-cyan-500/40 text-cyan-300"
                                        : "bg-white/4 border-white/10 text-white/40 hover:border-white/20"
                                }`}
                            >
                                <span className="text-sm" style={{fontWeight: 500}}>{label}</span>
                                <span className="text-xs text-white/25 mt-0.5">{desc}</span>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Time of day */}
                <div className="grid sm:grid-cols-2 gap-4">
                    <div>
                        <label
                            className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                            <Clock className="w-3.5 h-3.5"/> Time of day
                        </label>
                        <input
                            type="time"
                            value={timeOfDay}
                            onChange={(e) => setTimeOfDay(e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                        />
                        <p className="text-white/25 text-xs mt-1">UTC timezone</p>
                    </div>
                    <div>
                        <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 block">
                            Min. new records threshold
                        </label>
                        <input
                            type="number"
                            value={minRecords}
                            onChange={(e) => setMinRecords(e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                        />
                        <p className="text-white/25 text-xs mt-1">Retraining skipped if fewer records</p>
                    </div>
                </div>

                {/* Schedule preview */}
                {enabled && (
                    <div
                        className="flex items-center gap-2 bg-white/4 border border-white/8 rounded-xl px-4 py-3 text-xs text-white/40">
                        <RefreshCw className="w-3.5 h-3.5 text-white/25"/>
                        Next scheduled run:{" "}
                        <span className="text-white/60" style={{fontWeight: 500}}>
              {schedule === "daily" ? "Tomorrow" : schedule === "weekly" ? "Next Monday" : "1st of next month"} at {timeOfDay} UTC
            </span>
                        {" "}· skips if &lt; {parseInt(minRecords).toLocaleString()} new records
                    </div>
                )}
            </div>

            <button
                onClick={handleSave}
                disabled={saving}
                className="mt-5 flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/15"
                style={{fontWeight: 500}}
            >
                {saved ? <><Check className="w-4 h-4"/> Saved</> : saving ? "Saving…" : <><Save
                    className="w-4 h-4"/> Save schedule</>}
            </button>
        </div>
    );
}

// ─── Training results ─────────────────────────────────────────────────────────

function QualityBar({value, max = 100, color}: { value: number; max?: number; color: string }) {
    return (
        <div className="h-1.5 bg-white/8 rounded-full overflow-hidden">
            <div
                className="h-full rounded-full transition-all duration-700"
                style={{width: `${(value / max) * 100}%`, background: color}}
            />
        </div>
    );
}

function TrainingResults({engine}: { engine: InferenceEngine }) {
    const trainedDate = engine.trainedAt
        ? new Date(engine.trainedAt).toLocaleDateString("en-US", {year: "numeric", month: "long", day: "numeric"})
        : "Unknown";

    return (
        <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3 p-4 bg-emerald-500/8 border border-emerald-500/20 rounded-xl">
                <CheckCircle2 className="w-5 h-5 text-emerald-400 flex-shrink-0"/>
                <div>
                    <p className="text-emerald-400 text-sm" style={{fontWeight: 600}}>Engine trained</p>
                    <p className="text-white/30 text-xs">Trained on {trainedDate} · Algorithm: {engine.algorithm}</p>
                </div>
            </div>

            <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
                <div className="flex items-center gap-2 mb-4">
                    <Zap className="w-4 h-4 text-white/40"/>
                    <h3 className="text-white" style={{fontWeight: 600}}>Training configuration used</h3>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                    {[
                        {label: "Algorithm", value: engine.algorithm},
                        {label: "Epochs", value: engine.epochs?.toLocaleString() ?? "—"},
                        {label: "Learning rate", value: engine.learningRate ?? "—"},
                        {label: "Window size", value: engine.windowSize ? `${engine.windowSize} steps` : "—"},
                        {label: "Batch size", value: engine.batchSize ?? "—"},
                    ].map(({label, value}) => (
                        <div key={label} className="bg-white/4 rounded-xl p-3">
                            <p className="text-white/30 text-xs mb-1">{label}</p>
                            <p className="text-white text-sm font-mono" style={{fontWeight: 600}}>{String(value)}</p>
                        </div>
                    ))}
                </div>
            </div>

            {engine.inferredVariables && engine.inferredVariables.length > 0 && (
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
                    <div className="flex items-center gap-2 mb-4">
                        <BarChart3 className="w-4 h-4 text-white/40"/>
                        <h3 className="text-white" style={{fontWeight: 600}}>Inference quality</h3>
                    </div>
                    <div className="flex flex-col gap-4">
                        {engine.inferredVariables.map((v) => {
                            const violationColor = v.violations < 2 ? "#34d399" : v.violations < 5 ? "#fbbf24" : "#f87171";
                            const accuracyColor = v.accuracy > 90 ? "#34d399" : v.accuracy > 75 ? "#fbbf24" : "#f87171";
                            return (
                                <div key={v.name} className="bg-white/4 rounded-xl p-4">
                                    <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center gap-2">
                                            <span className="w-2 h-2 rounded-full bg-violet-400"/>
                                            <span className="text-white text-sm font-mono"
                                                  style={{fontWeight: 600}}>{v.name}</span>
                                        </div>
                                        <span className={`text-xs px-2 py-0.5 rounded-full border ${
                                            v.violations < 2
                                                ? "bg-emerald-500/10 border-emerald-500/20 text-emerald-400"
                                                : v.violations < 5
                                                    ? "bg-amber-500/10 border-amber-500/20 text-amber-400"
                                                    : "bg-red-500/10 border-red-500/20 text-red-400"
                                        }`}>
                      {v.violations}% violations
                    </span>
                                    </div>
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <div className="flex justify-between text-xs mb-1.5">
                                                <span className="text-white/40">Accuracy</span>
                                                <span className="text-white"
                                                      style={{fontWeight: 600}}>{v.accuracy}%</span>
                                            </div>
                                            <QualityBar value={v.accuracy} color={accuracyColor}/>
                                        </div>
                                        <div>
                                            <div className="flex justify-between text-xs mb-1.5">
                                                <span className="text-white/40">MAE</span>
                                                <span className="text-white font-mono"
                                                      style={{fontWeight: 600}}>{v.mae}</span>
                                            </div>
                                            <QualityBar value={Math.min(v.mae * 10, 100)} max={100} color="#a78bfa"/>
                                        </div>
                                    </div>
                                    <div className="mt-3">
                                        <div className="flex justify-between text-xs mb-1.5">
                                            <span className="text-white/40">Constraint violations</span>
                                            <span
                                                style={{color: violationColor, fontWeight: 600}}>{v.violations}%</span>
                                        </div>
                                        <QualityBar value={v.violations} color={violationColor}/>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}

// ─── Main ─────────────────────────────────────────────────────────────────────

interface Props {
    twin: DigitalTwin;
}

export function InferenceTab({twin}: Props) {
    const {updateTwin} = useApp();

    const handleSaveConfig = (engine: InferenceEngine) => {
        updateTwin(twin.id, {inferenceEngine: engine});
    };

    const handleTrainingComplete = (engine: InferenceEngine) => {
        updateTwin(twin.id, {inferenceEngine: engine});
    };

    const handleSaveRetraining = (cfg: RetrainingConfig) => {
        updateTwin(twin.id, {
            inferenceEngine: {...(twin.inferenceEngine ?? {trained: false, algorithm: "LSTM"}), retrainingConfig: cfg},
        });
    };

    return (
        <div className="flex flex-col gap-6 max-w-3xl">
            {/* 1 — Config */}
            <ConfigForm engine={twin.inferenceEngine} onSave={handleSaveConfig}/>

            {/* 2 — Launch */}
            <LaunchPanel twin={twin} onTrainingComplete={handleTrainingComplete}/>

            {/* 3 — Retraining schedule */}
            <RetrainingPanel
                config={twin.inferenceEngine?.retrainingConfig}
                onSave={handleSaveRetraining}
            />

            {/* 4 — Results divider */}
            <div className="flex items-center gap-3">
                <div className="flex-1 h-px bg-white/8"/>
                <div className="flex items-center gap-2 px-3">
                    <Cpu className="w-3.5 h-3.5 text-white/20"/>
                    <span className="text-white/20 text-xs uppercase tracking-wider">Last training results</span>
                </div>
                <div className="flex-1 h-px bg-white/8"/>
            </div>

            {/* 5 — Results or not-trained */}
            {twin.inferenceEngine?.trained ? (
                <TrainingResults engine={twin.inferenceEngine}/>
            ) : (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                    <div
                        className="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-4">
                        <AlertCircle className="w-6 h-6 text-white/20"/>
                    </div>
                    <p className="text-white/40 mb-1" style={{fontWeight: 500}}>No training run yet</p>
                    <p className="text-white/20 text-sm max-w-xs">
                        Configure the engine above and click "Start training" to see inference quality results here.
                    </p>
                </div>
            )}
        </div>
    );
}
