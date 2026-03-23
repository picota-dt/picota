import {useState, useCallback, useEffect} from "react";
import {useParams, useNavigate} from "react-router";
import {useApp} from "../context/AppContext";
import {PropertiesTab} from "../components/twin-detail/PropertiesTab";
import {MonitoringTab} from "../components/twin-detail/MonitoringTab";
import {ModelTab} from "../components/twin-detail/ModelTab";
import {DataTab} from "../components/twin-detail/DataTab";
import {InferenceTab} from "../components/twin-detail/InferenceTab";
import {
    Settings, Activity, Code2, Cpu, Tag, ChevronLeft, AlertTriangle, Database,
} from "lucide-react";

// ─── Tab definitions ──────────────────────────────────────────────────────────

type TabId = "properties" | "monitoring" | "model" | "data" | "inference";

const TABS: { id: TabId; label: string; icon: React.ElementType }[] = [
    {id: "properties", label: "Properties", icon: Settings},
    {id: "monitoring", label: "Monitoring", icon: Activity},
    {id: "model", label: "Model", icon: Code2},
    {id: "data", label: "Data", icon: Database},
    {id: "inference", label: "Inference Engine", icon: Cpu},
];

const STATUS_CONFIG = {
    active: {dot: "bg-emerald-400", text: "text-emerald-400", label: "Active"},
    draft: {dot: "bg-amber-400", text: "text-amber-400", label: "Draft"},
    offline: {dot: "bg-white/25", text: "text-white/40", label: "Offline"},
};

// ─── Leave-tab confirmation dialog ────────────────────────────────────────────

function UnsavedDialog({
                           onStay,
                           onLeave,
                       }: {
    onStay: () => void;
    onLeave: () => void;
}) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
            <div className="relative bg-[#1a1d27] border border-white/12 rounded-2xl w-full max-w-sm shadow-2xl p-6">
                <div className="flex items-center gap-3 mb-4">
                    <div
                        className="w-9 h-9 rounded-xl bg-amber-500/15 border border-amber-500/25 flex items-center justify-center">
                        <AlertTriangle className="w-4 h-4 text-amber-400"/>
                    </div>
                    <h2 className="text-white" style={{fontWeight: 600}}>Unsaved changes</h2>
                </div>
                <p className="text-white/40 text-sm mb-5">
                    You have unsaved changes in the Model tab. Leave without saving?
                </p>
                <div className="flex gap-3">
                    <button
                        onClick={onStay}
                        className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/50 hover:text-white/70 text-sm transition-colors"
                    >
                        Stay on Model
                    </button>
                    <button
                        onClick={onLeave}
                        className="flex-1 py-2.5 rounded-xl bg-red-500/20 hover:bg-red-500/30 border border-red-500/25 text-red-400 text-sm transition-colors"
                        style={{fontWeight: 500}}
                    >
                        Leave & discard
                    </button>
                </div>
            </div>
        </div>
    );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function TwinDetailPage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();
    const {getTwin, updateTwin} = useApp();

    const twin = getTwin(id ?? "");

    useEffect(() => {
        window.scrollTo({top: 0, behavior: "instant"});
    }, [id]);

    const [activeTab, setActiveTab] = useState<TabId>("properties");
    const [modelDirty, setModelDirty] = useState(false);
    const [pendingTab, setPendingTab] = useState<TabId | null>(null);
    const [showLeaveDialog, setShowLeaveDialog] = useState(false);

    if (!twin) {
        return (
            <div className="min-h-screen bg-[#0f1117] flex items-center justify-center flex-col gap-4">
                <p className="text-white/40">Twin not found.</p>
                <button
                    onClick={() => navigate("/twins")}
                    className="text-cyan-400 text-sm hover:text-cyan-300 transition-colors flex items-center gap-2"
                >
                    <ChevronLeft className="w-4 h-4"/>
                    Back to My Twins
                </button>
            </div>
        );
    }

    const status = STATUS_CONFIG[twin.status];

    const requestTabChange = (tab: TabId) => {
        if (tab === activeTab) return;
        if (activeTab === "model" && modelDirty) {
            setPendingTab(tab);
            setShowLeaveDialog(true);
        } else {
            setActiveTab(tab);
        }
    };

    const handleStay = () => {
        setPendingTab(null);
        setShowLeaveDialog(false);
    };

    const handleLeaveAndDiscard = () => {
        if (pendingTab) setActiveTab(pendingTab);
        setPendingTab(null);
        setModelDirty(false);
        setShowLeaveDialog(false);
    };

    // Called by ModelTab when it internally resolved the pending change (via save/discard in its own dialog)
    const handlePendingTabResolved = useCallback(() => {
        if (pendingTab) {
            setActiveTab(pendingTab);
            setPendingTab(null);
        }
        setShowLeaveDialog(false);
    }, [pendingTab]);

    const handleUpdateModel = (model: string, newVersion: string) => {
        updateTwin(twin.id, {model, version: newVersion, updatedAt: "Just now"});
    };

    const isModelTab = activeTab === "model";

    return (
        <div
            className={`bg-[#0f1117] flex flex-col ${isModelTab ? "h-[calc(100vh-56px)]" : "min-h-[calc(100vh-56px)]"}`}>

            {/* Page header */}
            <div className="border-b border-white/6 bg-[#0f1117] flex-shrink-0">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-5">
                    <div className="flex items-start gap-4">
                        {/* Thumbnail */}
                        <div
                            className="hidden sm:block w-14 h-14 rounded-xl overflow-hidden flex-shrink-0 bg-[#1a1d27] border border-white/8">
                            <img src={twin.image} alt={twin.name} className="w-full h-full object-cover"/>
                        </div>

                        <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 flex-wrap mb-1">
                                <h1 className="text-white" style={{fontWeight: 700, fontSize: "1.25rem"}}>
                                    {twin.name}
                                </h1>
                                <span
                                    className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/6 border border-white/10 text-xs ${status.text}`}
                                    style={{fontWeight: 500}}>
                  <span
                      className={`w-1.5 h-1.5 rounded-full ${status.dot} ${twin.status === "active" ? "animate-pulse" : ""}`}/>
                                    {status.label}
                </span>
                                <span
                                    className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/6 border border-white/10 text-xs text-white/40">
                  <Tag className="w-3 h-3"/>
                                    {twin.type}
                </span>
                                <span
                                    className="text-white/30 text-xs font-mono px-2 py-1 bg-white/4 rounded-lg border border-white/8">
                  v{twin.version}
                </span>
                            </div>
                            <p className="text-white/35 text-sm line-clamp-1">{twin.description}</p>
                        </div>
                    </div>
                </div>

                {/* Tab bar */}
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex gap-1 overflow-x-auto pb-px">
                        {TABS.map(({id: tabId, label, icon: Icon}) => (
                            <button
                                key={tabId}
                                onClick={() => requestTabChange(tabId)}
                                className={`flex items-center gap-2 px-4 py-3 text-sm whitespace-nowrap border-b-2 transition-all flex-shrink-0 ${
                                    activeTab === tabId
                                        ? "border-cyan-400 text-cyan-300"
                                        : "border-transparent text-white/40 hover:text-white/70 hover:border-white/20"
                                }`}
                            >
                                <Icon className="w-4 h-4"/>
                                {label}
                                {tabId === "model" && modelDirty && (
                                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0"/>
                                )}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Tab content */}
            <div className={`flex-1 min-h-0 ${isModelTab ? "flex flex-col overflow-hidden" : "overflow-y-auto"}`}>
                {activeTab === "properties" && (
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                        <PropertiesTab twin={twin}/>
                    </div>
                )}

                {activeTab === "monitoring" && (
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                        <MonitoringTab twin={twin}/>
                    </div>
                )}

                {activeTab === "model" && (
                    <ModelTab
                        twin={twin}
                        onUpdateModel={handleUpdateModel}
                        onDirtyChange={setModelDirty}
                        pendingTabChange={pendingTab}
                        onPendingTabResolved={handlePendingTabResolved}
                    />
                )}

                {activeTab === "data" && (
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                        <DataTab twin={twin}/>
                    </div>
                )}

                {activeTab === "inference" && (
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                        <InferenceTab twin={twin}/>
                    </div>
                )}
            </div>

            {/* Leave-model dialog (when no internal dialog from ModelTab) */}
            {showLeaveDialog && (
                <UnsavedDialog onStay={handleStay} onLeave={handleLeaveAndDiscard}/>
            )}
        </div>
    );
}