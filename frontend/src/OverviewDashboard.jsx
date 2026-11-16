import React from 'react';
import {Package,Box,ChartNoAxesColumn,ShieldCheck,ArrowUpRight,ChevronDown,Layers3} from 'lucide-react';

const number=n=>Number(n||0).toLocaleString('en-US');
const money=n=>Number(n||0).toLocaleString('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2});
const colors=['#bcb0f3','#242424','#dbf36c','#e0dafa','#9d97ab'];

export default function OverviewDashboard({products,dashboard,sales,days,busy,onDaysChange,onInventory,onLowStock}) {
 const categories=Object.entries(products.reduce((all,p)=>{all[p.category]=(all[p.category]||0)+p.quantity;return all;},{})).sort((a,b)=>b[1]-a[1]);
 const segments=categories.length>5?[...categories.slice(0,4),['Other',categories.slice(4).reduce((sum,v)=>sum+v[1],0)]]:categories;
 const total=Number(dashboard.units||0),count=products.length,low=products.filter(p=>p.quantity<=p.reorderLevel).length;
 const healthy=count-low,health=count?healthy/count:0;
 const daily=sales.daily||[],maxRevenue=Math.max(1,...daily.map(d=>Number(d.revenue)));
 const today=new Date().toLocaleDateString('en-US',{day:'numeric',month:'short',year:'numeric'});
 return <>
  <div className="overview-date"><span>{today}</span><label className="period-picker"><select aria-label="Sales period" value={days} disabled={busy} onChange={e=>onDaysChange(Number(e.target.value))}><option value="7">Last 7 days</option><option value="30">Last 30 days</option></select><ChevronDown size={16}/></label></div>
  <div className="overview-bento">
   <section className="bento-card distribution-card">
    <div className="bento-card-heading"><span className="metric-icon"><Box size={19}/></span><h2>Stock distribution</h2><button className="icon" aria-label="View all inventory" onClick={onInventory}><ArrowUpRight size={19}/></button></div>
    <div className="distribution-total"><strong>{number(total)}</strong><span>units in stock</span></div>
    {total>0?<div className="bubble-chart" role="img" aria-label={segments.map(([name,value])=>`${name}: ${value} units`).join(', ')}>{segments.slice(0,3).map(([name,value],i)=><div key={name} className={`stock-bubble bubble-${i}`} style={{'--bubble-color':colors[i]}}><strong>{number(value)}</strong><span>units</span></div>)}</div>:<div className="bubble-empty"><Layers3 size={50}/><span>Add products to see your stock mix.</span></div>}
    <div className="distribution-legend">{segments.map(([name,value],i)=><div className="distribution-row" key={name}><div><strong>{total?Math.round(value/total*100):0}<small>%</small></strong><span>{name}<i style={{background:colors[i]}}/></span></div><div className="distribution-track"><span style={{width:`${total?value/total*100:0}%`,background:colors[i]}}/></div></div>)}</div>
    <div className="distribution-foot"><span>By category</span><span>{categories.length} categories</span></div>
   </section>
   <section className="bento-card catalog-card"><div className="bento-card-heading"><span className="metric-icon"><Package size={19}/></span><h2>Product catalog</h2><button className="icon" aria-label="Browse products" onClick={onInventory}><ArrowUpRight size={18}/></button></div><div className="compact-values"><strong>{number(dashboard.products)}</strong><div><b>{number(dashboard.suppliers)} suppliers</b><span>Across your catalog</span></div></div></section>
   <section className="bento-card value-card"><div className="bento-card-heading"><span className="metric-icon"><Layers3 size={19}/></span><h2>Inventory value</h2></div><div className="compact-values"><strong className="money-value">{money(dashboard.inventoryValue)}</strong></div><p className="card-caption">Current stock × unit price</p></section>
   <section className="bento-card health-card"><div className="bento-card-heading"><span className="metric-icon"><ShieldCheck size={19}/></span><h2>Stock health</h2><button className="icon" aria-label="Review low-stock products" onClick={onLowStock}><ArrowUpRight size={18}/></button></div><div className="health-score"><strong>{count?(health*100).toFixed(1):'—'}<span>{count?'%':''}</span></strong><span>{count?'Above reorder level':'No products yet'}</span></div><div className="health-dot-grid" role="img" aria-label={`${healthy} of ${count} products are above their reorder level.`}>{Array.from({length:84},(_,i)=><span key={i} className={i<Math.round(health*84)?'filled':''} style={{'--dot-opacity':i%4===0?.62:i%3===0?.82:1}}/>)}</div><div className="health-legend"><span><i/>{healthy} healthy</span><button onClick={onLowStock}><i/>{low} need attention <ArrowUpRight size={13}/></button></div></section>
   <section className="sales-card"><div className="bento-card-heading"><span className="metric-icon"><ChartNoAxesColumn size={19}/></span><h2>Sales activity</h2><span className="sales-period">Last {days} days</span></div><div className="sales-metrics"><div><i/><strong>{money(sales.revenue)}</strong><span>Recorded revenue</span></div><div><i/><strong>{number(sales.orders)}</strong><span>Completed sales</span></div></div><div className={'revenue-chart '+(days===30?'dense':'')} role="img" aria-label={`Daily recorded sales revenue over ${days} days. Total ${money(sales.revenue)}.`}>{daily.map((d,i)=><div className="revenue-column" key={d.date} title={`${d.date}: ${money(d.revenue)}`}><div className="revenue-track"><span className={i%2===0?'lime':'lavender'} style={{height:Number(d.revenue)>0?`${Math.max(4,Number(d.revenue)/maxRevenue*100)}%`:'0%'}}/></div><small>{days===7?new Date(d.date+'T12:00:00Z').toLocaleDateString('en-US',{weekday:'short',timeZone:'UTC'}):(i%5===0||i===daily.length-1?d.date.slice(8):'')}</small></div>)}</div><div className="sales-chart-note"><span><i/>Daily sales revenue · USD</span><span>UTC calendar days</span></div></section>
  </div>
 </>;
}
