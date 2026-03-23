import {useState} from "react";
import {useApp} from "../context/AppContext";
import {
    User, Mail, Building2, Calendar, Coins, Plus, Check,
    ShieldCheck, Bell, Key, Trash2
} from "lucide-react";

const CREDIT_PACKS = [
    {credits: 500, price: "$9", label: "Starter"},
    {credits: 2000, price: "$29", label: "Growth", popular: true},
    {credits: 10000, price: "$99", label: "Scale"},
];

export default function ProfilePage() {
    const {user} = useApp();
    const [bought, setBought] = useState<number | null>(null);
    const [showBuyModal, setShowBuyModal] = useState(false);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 700));
        setSaving(false);
        setSaved(true);
        setTimeout(() => setSaved(false), 2500);
    };

    const handleBuy = async (idx: number) => {
        setBought(idx);
        await new Promise((r) => setTimeout(r, 900));
        setBought(null);
        setShowBuyModal(false);
    };

    return (
        <div className="min-h-screen bg-[#0f1117] py-8 px-4">
            <div className="max-w-2xl mx-auto flex flex-col gap-5">

                {/* Header card */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6 flex items-center gap-5">
                    <div
                        className="w-16 h-16 rounded-2xl bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white text-xl flex-shrink-0"
                        style={{fontWeight: 700}}>
                        {user.avatarInitials}
                    </div>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-white" style={{fontWeight: 700, fontSize: "1.15rem"}}>{user.name}</h1>
                        <p className="text-white/40 text-sm">{user.email}</p>
                        <div className="flex items-center gap-2 mt-1.5">
              <span className="bg-cyan-500/15 text-cyan-400 text-xs px-2 py-0.5 rounded-full border border-cyan-500/20"
                    style={{fontWeight: 500}}>
                {user.role}
              </span>
                            <span className="text-white/25 text-xs">{user.organization}</span>
                        </div>
                    </div>
                </div>

                {/* Credits card */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-2">
                            <Coins className="w-4 h-4 text-amber-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Credits</h2>
                        </div>
                        <button
                            onClick={() => setShowBuyModal(true)}
                            className="flex items-center gap-1.5 bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/30 text-amber-400 px-3.5 py-1.5 rounded-xl text-sm transition-all"
                            style={{fontWeight: 500}}
                        >
                            <Plus className="w-3.5 h-3.5"/>
                            Buy credits
                        </button>
                    </div>

                    <div className="flex items-end gap-2 mb-3">
                        <span className="text-white"
                              style={{fontWeight: 800, fontSize: "2.5rem"}}>{user.credits.toLocaleString()}</span>
                        <span className="text-white/40 text-sm mb-2">credits remaining</span>
                    </div>

                    {/* Visual bar */}
                    <div className="h-2 bg-white/6 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-gradient-to-r from-amber-400 to-amber-500 rounded-full"
                            style={{width: `${Math.min((user.credits / 5000) * 100, 100)}%`}}
                        />
                    </div>
                    <p className="text-white/25 text-xs mt-2">Credits are consumed when active twins run inference</p>
                </div>

                {/* Profile info */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <h2 className="text-white mb-5" style={{fontWeight: 600}}>Profile information</h2>
                    <div className="flex flex-col gap-4">
                        {[
                            {icon: User, label: "Full name", value: user.name, placeholder: user.name},
                            {icon: Mail, label: "Email address", value: user.email, placeholder: user.email},
                            {
                                icon: Building2,
                                label: "Organization",
                                value: user.organization,
                                placeholder: user.organization
                            },
                        ].map(({icon: Icon, label, value, placeholder}) => (
                            <div key={label}>
                                <label
                                    className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                                    <Icon className="w-3.5 h-3.5"/>
                                    {label}
                                </label>
                                <input
                                    defaultValue={value}
                                    placeholder={placeholder}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                                />
                            </div>
                        ))}
                        <div>
                            <label
                                className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                                <Calendar className="w-3.5 h-3.5"/>
                                Member since
                            </label>
                            <p className="text-white/50 text-sm px-3.5 py-2.5">{user.joinedAt}</p>
                        </div>
                    </div>
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="mt-5 flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/20"
                        style={{fontWeight: 500}}
                    >
                        {saved ? (
                            <><Check className="w-4 h-4"/> Saved</>
                        ) : saving ? (
                            "Saving…"
                        ) : (
                            "Save changes"
                        )}
                    </button>
                </div>

                {/* Security & preferences */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <h2 className="text-white mb-4" style={{fontWeight: 600}}>Security & preferences</h2>
                    <div className="flex flex-col gap-2">
                        {[
                            {icon: Key, label: "Change password", sub: "Update your login credentials"},
                            {icon: ShieldCheck, label: "Two-factor authentication", sub: "Not enabled"},
                            {icon: Bell, label: "Notification preferences", sub: "Email · In-app"},
                        ].map(({icon: Icon, label, sub}) => (
                            <button
                                key={label}
                                className="flex items-center gap-3 w-full text-left px-4 py-3 rounded-xl hover:bg-white/5 transition-colors group"
                            >
                                <div
                                    className="w-8 h-8 rounded-lg bg-white/6 flex items-center justify-center flex-shrink-0">
                                    <Icon
                                        className="w-4 h-4 text-white/40 group-hover:text-white/60 transition-colors"/>
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="text-white/70 text-sm group-hover:text-white transition-colors"
                                       style={{fontWeight: 500}}>{label}</p>
                                    <p className="text-white/30 text-xs">{sub}</p>
                                </div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Danger zone */}
                <div className="bg-red-500/5 border border-red-500/15 rounded-2xl p-6">
                    <h2 className="text-red-400 mb-1" style={{fontWeight: 600}}>Danger zone</h2>
                    <p className="text-white/30 text-sm mb-4">This action is permanent and cannot be undone.</p>
                    <button
                        className="flex items-center gap-2 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 px-4 py-2.5 rounded-xl text-sm transition-all">
                        <Trash2 className="w-4 h-4"/>
                        Delete account
                    </button>
                </div>
            </div>

            {/* Buy credits modal */}
            {showBuyModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4"
                    onClick={(e) => {
                        if (e.target === e.currentTarget) setShowBuyModal(false);
                    }}
                >
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
                    <div
                        className="relative bg-[#1a1d27] border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
                        <div className="flex items-center gap-2 mb-5">
                            <Coins className="w-5 h-5 text-amber-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Buy credits</h2>
                        </div>
                        <div className="flex flex-col gap-3">
                            {CREDIT_PACKS.map((pack, idx) => (
                                <button
                                    key={pack.label}
                                    onClick={() => handleBuy(idx)}
                                    disabled={bought !== null}
                                    className={`relative flex items-center justify-between px-4 py-3.5 rounded-xl border transition-all ${
                                        pack.popular
                                            ? "bg-cyan-500/15 border-cyan-500/40 hover:bg-cyan-500/25"
                                            : "bg-white/4 border-white/10 hover:border-white/20"
                                    }`}
                                >
                                    {pack.popular && (
                                        <span
                                            className="absolute -top-2 left-4 bg-cyan-500 text-white text-xs px-2 py-0.5 rounded-full"
                                            style={{fontWeight: 600}}>
                      Most popular
                    </span>
                                    )}
                                    <div className="text-left">
                                        <p className="text-white text-sm" style={{fontWeight: 600}}>{pack.label}</p>
                                        <p className="text-white/40 text-xs">{pack.credits.toLocaleString()} credits</p>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {bought === idx ? (
                                            <span className="text-cyan-400 text-sm">Processing…</span>
                                        ) : (
                                            <span className="text-white"
                                                  style={{fontWeight: 700, fontSize: "1.1rem"}}>{pack.price}</span>
                                        )}
                                    </div>
                                </button>
                            ))}
                        </div>
                        <button
                            onClick={() => setShowBuyModal(false)}
                            className="w-full mt-4 py-2 text-white/30 hover:text-white/50 text-sm transition-colors"
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
