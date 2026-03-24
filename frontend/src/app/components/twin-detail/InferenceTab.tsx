import {useEffect, useRef, useState} from "react";
import {DigitalTwin, InferenceEngine, RetrainingConfig, TrainingJob, useApp} from "../../context/AppContext";
import {
    AlertCircle,
    BarChart3,
    Calendar,
    Check,
    CheckCircle2,
    Clock,
    Cpu,
    Info,
    Play,
    RefreshCw,
    Save,
    Settings2,
    ToggleLeft,
    ToggleRight,
    Zap,
} from "lucide-react";

// ─── Config form ──────────────────────────────────────────────────────────────

const ALGORITHMS = ["KAN", "TabNet"] as const;
const DEFAULT_ALGORITHM = "KAN";
const DEFAULT_EPOCHS = 50;
const DEFAULT_WINDOW_SIZE = 0;
const DEFAULT_LEARNING_RATE = 0.0005;
const DEFAULT_BATCH_SIZE = 64;

function normalizeAlgorithm(algorithm: string | undefined): string {
    return ALGORITHMS.includes((algorithm ?? "") as (typeof ALGORITHMS)[number])
        ? algorithm!
        : DEFAULT_ALGORITHM;
}

interface ConfigFormProps {
    engine: InferenceEngine | null;
    onSave: (engine: InferenceEngine) => void;
}

function ConfigForm({engine, onSave}: ConfigFormProps) {
    const [algorithm, setAlgorithm] = useState(normalizeAlgorithm(engine?.algorithm));
    const [epochs, setEpochs] = useState(String(engine?.epochs ?? DEFAULT_EPOCHS));
    const [learningRate, setLearningRate] = useState(String(engine?.learningRate ?? DEFAULT_LEARNING_RATE));
    const [windowSize, setWindowSize] = useState(String(engine?.windowSize ?? DEFAULT_WINDOW_SIZE));
    const [batchSize, setBatchSize] = useState(String(engine?.batchSize ?? DEFAULT_BATCH_SIZE));
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 700));
        const parsedEpochs = Number.parseInt(epochs, 10);
        const parsedLearningRate = Number.parseFloat(learningRate);
        const parsedWindowSize = Number.parseInt(windowSize, 10);
        const parsedBatchSize = Number.parseInt(batchSize, 10);
        onSave({
            ...(engine ?? {}),
            trained: engine?.trained ?? false,
            algorithm,
            epochs: Number.isFinite(parsedEpochs) ? parsedEpochs : DEFAULT_EPOCHS,
            learningRate: Number.isFinite(parsedLearningRate) ? parsedLearningRate : DEFAULT_LEARNING_RATE,
            windowSize: Number.isFinite(parsedWindowSize) ? parsedWindowSize : DEFAULT_WINDOW_SIZE,
            batchSize: Number.isFinite(parsedBatchSize) ? parsedBatchSize : DEFAULT_BATCH_SIZE,
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
    {phase: "error", label: "Training failed", pct: 100},
];

interface LaunchPanelProps {
    twin: DigitalTwin;
    onTrainingComplete: (engine: InferenceEngine) => void;
    launchTrainingJob: (twinId: string) => Promise<TrainingJob>;
    getTrainingJob: (twinId: string, jobId: string) => Promise<TrainingJob>;
    onJumpToResults: () => void;
}

function LaunchPanel({twin, onTrainingComplete, launchTrainingJob, getTrainingJob, onJumpToResults}: LaunchPanelProps) {
    const [phase, setPhase] = useState<TrainingPhase>("idle");
    const [progress, setProgress] = useState(0);
    const [log, setLog] = useState<string[]>([]);
    const [activeJobId, setActiveJobId] = useState<string | null>(null);
    const logRef = useRef<HTMLDivElement>(null);
    const latestStatus = useRef<TrainingJob["status"] | null>(null);
    const engine = twin.inferenceEngine;

    const hasData = (twin.datasets ?? []).some((d) => d.uploadedRecords > 0);
    const hasConfig = engine !== null;

    const addLog = (msg: string) =>
        setLog((prev) => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);

    const runTraining = async () => {
        if (!hasConfig || !hasData) return;

        setLog([]);
        setProgress(0);
        setPhase("preparing");
        latestStatus.current = null;
        setActiveJobId(null);

        try {
            addLog("Submitting training request…");
            const started = await launchTrainingJob(twin.id);
            setActiveJobId(started.jobId);
            syncFromJob(started, addLog, setPhase, setProgress, latestStatus);

            if (started.status === "done") {
                if (started.result) onTrainingComplete(started.result);
                addLog("✓ Training complete. Model saved.");
                setPhase("done");
                setProgress(100);
                return;
            }
            if (started.status === "failed") {
                addLog(`✗ ${started.errorMessage ?? "Training failed"}`);
                setPhase("error");
                setProgress(100);
                return;
            }

            for (let poll = 0; poll < 400; poll++) {
                await sleep(1500);
                const current = await getTrainingJob(twin.id, started.jobId);
                syncFromJob(current, addLog, setPhase, setProgress, latestStatus);
                if (current.status === "done") {
                    if (current.result) onTrainingComplete(current.result);
                    addLog("✓ Training complete. Model saved.");
                    setPhase("done");
                    setProgress(100);
                    return;
                }
                if (current.status === "failed") {
                    addLog(`✗ ${current.errorMessage ?? "Training failed"}`);
                    setPhase("error");
                    setProgress(100);
                    return;
                }
            }
            addLog("✗ Training status timed out");
            setPhase("error");
        } catch (error) {
            const message = error instanceof Error ? error.message : "Training request failed";
            addLog(`✗ ${message}`);
            setPhase("error");
        }
    }

    useEffect(() => {
        if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
    }, [log]);

    const isRunning = phase === "preparing" || phase === "training" || phase === "evaluating";

    const phaseInfo = PHASES.find((p) => p.phase === phase) ?? {phase: "idle" as TrainingPhase, label: "Idle", pct: 0};

    return (
        <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2">
                    <Play className="w-4 h-4 text-white/40"/>
                    <h3 className="text-white" style={{fontWeight: 600}}>Launch training</h3>
                </div>
                {(phase === "done" || phase === "error") && (
                    <span
                        className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full border ${
                            phase === "done"
                                ? "text-emerald-400 bg-emerald-500/10 border-emerald-500/20"
                                : "text-red-400 bg-red-500/10 border-red-500/20"
                        }`}>
            <CheckCircle2 className="w-3.5 h-3.5"/>
                        {phase === "done" ? "Completed" : "Failed"}
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
            <span
                className={`${phase === "done" ? "text-emerald-400" : phase === "error" ? "text-red-400" : "text-cyan-400"}`}
                style={{fontWeight: 500}}>
              {phaseInfo?.label ?? "Processing…"}
            </span>
                        <span className="text-white/40 font-mono">{Math.round(progress)}%</span>
                    </div>
                    <div className="h-2 bg-white/8 rounded-full overflow-hidden">
                        <div
                            className={`h-full rounded-full transition-all duration-300 ${
                                phase === "done"
                                    ? "bg-gradient-to-r from-emerald-500 to-emerald-400"
                                    : phase === "error"
                                        ? "bg-gradient-to-r from-red-500 to-red-400"
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
                                        line.includes("✗") ? "text-red-400" :
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
                    {phase === "done" && (
                        <button
                            onClick={onJumpToResults}
                            className="mt-3 inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-cyan-500/40 bg-cyan-500/10 text-cyan-300 text-xs hover:bg-cyan-500/20 transition-all"
                            style={{fontWeight: 500}}
                        >
                            <BarChart3 className="w-3.5 h-3.5"/>
                            View last training results
                        </button>
                    )}
                </div>
            )}

            {/* Launch button */}
            <button
                onClick={runTraining}
                disabled={isRunning || !hasConfig || !hasData}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm transition-all ${
                    phase === "done"
                        ? "bg-white/8 border border-white/15 text-white/50 hover:text-white/70 hover:bg-white/12"
                        : phase === "error"
                            ? "bg-red-500/15 border border-red-500/30 text-red-300 hover:bg-red-500/25"
                            : "bg-gradient-to-r from-cyan-600 to-cyan-500 hover:from-cyan-500 hover:to-cyan-400 text-white shadow-lg shadow-cyan-500/20 disabled:opacity-40 disabled:cursor-not-allowed"
                }`}
                style={{fontWeight: 500}}
            >
                {isRunning ? (
                    <>
                        <RefreshCw className="w-4 h-4 animate-spin"/>
                        Training in progress…
                    </>
                ) : phase === "done" ? (
                    <>
                        <RefreshCw className="w-4 h-4"/>
                        Retrain
                    </>
                ) : phase === "error" ? (
                    <>
                        <RefreshCw className="w-4 h-4"/>
                        Retry training
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
            {activeJobId && (
                <p className="text-white/25 text-xs mt-2 font-mono">
                    Ticket: {activeJobId}
                </p>
            )}
        </div>
    );
}

function syncFromJob(
    job: TrainingJob,
    addLog: (message: string) => void,
    setPhase: (phase: TrainingPhase) => void,
    setProgress: (progress: number) => void,
    latestStatus: { current: TrainingJob["status"] | null }
) {
    const nextPhase = phaseFromStatus(job.status);
    setPhase(nextPhase);
    setProgress(resolveJobProgress(job));

    if (latestStatus.current !== job.status) {
        latestStatus.current = job.status;
        addLog(job.currentPhase ?? defaultPhaseLabel(job.status));
    }
}

function phaseFromStatus(status: TrainingJob["status"]): TrainingPhase {
    switch (status) {
        case "queued":
        case "preparing":
            return "preparing";
        case "training":
            return "training";
        case "evaluating":
            return "evaluating";
        case "done":
            return "done";
        case "failed":
            return "error";
        default:
            return "idle";
    }
}

function defaultPhaseLabel(status: TrainingJob["status"]): string {
    switch (status) {
        case "queued":
            return "Queued";
        case "preparing":
            return "Preparing dataset…";
        case "training":
            return "Training in progress…";
        case "evaluating":
            return "Evaluating model…";
        case "done":
            return "Training complete";
        case "failed":
            return "Training failed";
        default:
            return "Training update";
    }
}

function resolveJobProgress(job: TrainingJob): number {
    if (typeof job.progress === "number" && Number.isFinite(job.progress)) {
        return Math.max(0, Math.min(100, Math.round(job.progress)));
    }
    switch (job.status) {
        case "queued":
            return 5;
        case "preparing":
            return 15;
        case "training":
            return 65;
        case "evaluating":
            return 90;
        case "done":
        case "failed":
            return 100;
        default:
            return 0;
    }
}

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
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

function formatMetric(value: number | null | undefined, decimals = 4): string {
    if (value === null || value === undefined || Number.isNaN(value)) return "—";
    return Number(value).toFixed(decimals);
}

function formatValidationCount(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return "—";
    return Math.max(0, Math.trunc(value)).toLocaleString();
}

function formatValidationDuration(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return "—";
    if (value < 1) return `${Math.round(value * 1000)} ms`;
    return `${value.toFixed(2)} s`;
}

function formatAccuracy(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return "—";
    const percentage = Math.abs(value) <= 1 ? value * 100 : value;
    return `${percentage.toFixed(2)}%`;
}

function normalizePercentage(value: number | null | undefined): number | null {
    if (value === null || value === undefined || Number.isNaN(value)) return null;
    if (Math.abs(value) <= 1) return value * 100;
    return value;
}

function parseDateLike(value: unknown): Date | null {
    if (value === null || value === undefined) return null;
    if (typeof value === "number" && Number.isFinite(value)) {
        const millis = Math.abs(value) < 1_000_000_000_000 ? value * 1000 : value;
        const parsed = new Date(millis);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed.length === 0) return null;
        const numeric = Number(trimmed);
        if (Number.isFinite(numeric)) {
            return parseDateLike(numeric);
        }
        const parsed = new Date(trimmed);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
}

function TrainingResults({engine}: { engine: InferenceEngine }) {
    const parsedTrainedAt = parseDateLike(engine.trainedAt);
    const trainedDate = parsedTrainedAt
        ? parsedTrainedAt.toLocaleDateString("en-US", {year: "numeric", month: "long", day: "numeric"})
        : "—";

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
                        {
                            label: "Window size",
                            value: engine.windowSize === null || engine.windowSize === undefined ? "—" : `${engine.windowSize} steps`,
                        },
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
                            const isCategorical = v.dataType === "categorical"
                                || v.accuracy !== undefined
                                || v.macroF1 !== undefined;
                            const totalViolationPercent = normalizePercentage(v.violations);
                            const constraintViolations = Object.entries(v.constraintViolations ?? {})
                                .map(([constraint, rate]) => ({constraint, rate: normalizePercentage(rate)}))
                                .filter((entry) => entry.rate !== null);
                            const hasViolationMetrics = totalViolationPercent !== null || constraintViolations.length > 0;
                            const orderedMetrics = [
                                {label: "Test n", value: formatValidationCount(v.validationSampleCount)},
                                {label: "Test time", value: formatValidationDuration(v.validationDurationSeconds)},
                                {label: "MAE", value: formatMetric(v.mae)},
                                {label: "R2", value: formatMetric(v.r2)},
                                ...(isCategorical ? [
                                    {label: "Accuracy", value: formatAccuracy(v.accuracy)},
                                    {label: "Macro F1", value: formatMetric(v.macroF1)},
                                ] : []),
                            ];
                            return (
                                <div key={v.name} className="bg-white/4 rounded-xl p-4">
                                    <div className="flex items-center justify-between mb-4">
                                        <div className="flex items-center gap-2">
                                            <span className="w-2 h-2 rounded-full bg-violet-400"/>
                                            <span className="text-white text-sm font-mono"
                                                  style={{fontWeight: 600}}>{v.name}</span>
                                        </div>
                                        <span
                                            className="text-xs px-2 py-0.5 rounded-full border bg-white/4 border-white/10 text-white/45">
                                            {isCategorical ? "Categorical" : "Numeric"}
                                        </span>
                                    </div>
                                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                                        {orderedMetrics.map((metric) => (
                                            <div key={`${v.name}-${metric.label}`}
                                                 className="bg-white/4 rounded-lg p-3">
                                                <p className="text-white/30 text-[11px] mb-1">{metric.label}</p>
                                                <p className="text-white font-mono text-sm"
                                                   style={{fontWeight: 600}}>{metric.value}</p>
                                            </div>
                                        ))}
                                    </div>
                                    {hasViolationMetrics && (
                                        <div className="mt-3 bg-white/3 border border-white/8 rounded-lg p-3">
                                            <p className="text-white/35 text-[11px] mb-2">Constraint violations</p>
                                            {totalViolationPercent !== null && (
                                                <p className="text-white/70 text-xs mb-2">
                                                    Total: <span
                                                    className="font-mono text-white">{totalViolationPercent.toFixed(2)}%</span>
                                                </p>
                                            )}
                                            {constraintViolations.length > 0 && (
                                                <div className="flex flex-wrap gap-2">
                                                    {constraintViolations.map((entry) => (
                                                        <span
                                                            key={`${v.name}-${entry.constraint}`}
                                                            className="text-[11px] px-2 py-1 rounded-md border border-white/10 bg-white/5 text-white/65 font-mono"
                                                        >
                                                            {entry.constraint}: {entry.rate!.toFixed(2)}%
                                                        </span>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    )}
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
    const {updateTwin, setTwins, launchTrainingJob, getTrainingJob} = useApp();
    const lastResultsRef = useRef<HTMLDivElement>(null);

    const handleSaveConfig = (engine: InferenceEngine) => {
        updateTwin(twin.id, {inferenceEngine: engine});
    };

    const handleTrainingComplete = (engine: InferenceEngine) => {
        setTwins((prev) =>
            prev.map((candidate) =>
                candidate.id === twin.id
                    ? {...candidate, inferenceEngine: engine}
                    : candidate
            )
        );
    };

    const handleSaveRetraining = (cfg: RetrainingConfig) => {
        updateTwin(twin.id, {
            inferenceEngine: {
                ...(twin.inferenceEngine ?? {trained: false, algorithm: DEFAULT_ALGORITHM}),
                retrainingConfig: cfg
            },
        });
    };

    const handleJumpToResults = () => {
        lastResultsRef.current?.scrollIntoView({behavior: "smooth", block: "start"});
    };

    return (
        <div className="flex flex-col gap-6 max-w-3xl">
            {/* 1 — Config */}
            <ConfigForm engine={twin.inferenceEngine} onSave={handleSaveConfig}/>

            {/* 2 — Launch */}
            <LaunchPanel
                twin={twin}
                onTrainingComplete={handleTrainingComplete}
                launchTrainingJob={launchTrainingJob}
                getTrainingJob={getTrainingJob}
                onJumpToResults={handleJumpToResults}
            />

            {/* 3 — Retraining schedule */}
            <RetrainingPanel
                config={twin.inferenceEngine?.retrainingConfig}
                onSave={handleSaveRetraining}
            />

            {/* 4 — Results divider */}
            <div ref={lastResultsRef} className="flex items-center gap-3">
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
