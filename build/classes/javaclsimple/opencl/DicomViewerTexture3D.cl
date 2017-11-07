//#define TEXTURE_OUT_FLOAT



#define H_COEFF .75f
#define AO
#define AO_NB_SAMPLE 5
#define SHADOW
//#define ANAGLYPH
//#define RD_Anaglyph RD_Anaglyph_OffAxis  
//#define RD_Anaglyph RD_Anaglyph_ToeIn

__constant int3 Z3 = (int3)(0);
__constant float3 ep = (float3)(1.f,-1.f,0.f); 

const sampler_t sampler_linear = CLK_NORMALIZED_COORDS_FALSE |  CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

typedef struct Quality {
    float dd;     // pas lors du parcours des rayons dans le volume
    int dPrec;    // nombre de dychotomie pour preciser la surface
    int ao_nb;    // nombre de rayons pour l'ambiant occlusion (0 = desactivé)
    float ao_dd;  // pas lors du parcours des rayons de l'ambiant occlusion 
    float ao_dmax;// distance consideree pour l'ambiant occlusion
    int sh_on;    // (1/0) activation / desactivation des ombres portees
    float sh_dd;  // pas lors du parcours des rayons des ombres portees 
    float sh_dmax;// distance consideree pour les ombres portees
} Quality;

__constant Quality hq = {1.f,6, 10,3.f,36.f, 1,2.f,180.f};
__constant Quality lq = {5.f,6,  0,6.f,36.f, 1,6.f,180.f};


inline float valueAt(const float3 p, read_only image3d_t dem) {
    return read_imagef(dem, sampler_linear, (float4)(p.x, p.y, p.z*H_COEFF, 0.f)).x;
}


inline float map(const float a0, const float b0, const float a1, const float b1, const float v) {
    return a1 + (v-a0)*(b1-a1)/(b0-a0);
}

inline float invMix(const float v0, const float v1, const float v) {
    return (v-v0)/(v1-v0);
}

//------------------------------------------------------
inline float3 reflect(const float3 v, const float3 n){
  return v - 2.f * n * dot(n,v);
}

// eta = Specifies the ratio of indices of refraction
inline float refract(const float3 I, const float3 N, const float eta, float3 * R) {
  const float cosNI = dot( N, I );
  const float k = 1.f - eta*eta*(1.f - cosNI*cosNI);
  if( k > 0.f ){
	  R->xyz = (eta*I) - (eta*cosNI + sqrt(k)) * N;
    return 1.f;
  } else {
    R->xyz = reflect(I, N);
    return 0.f;
  }
}

// ------------------------------------------------------------------
/*
float3 normalAt(const float3 p, read_only image3d_t buf, const int3 sz) {
	return normalize((float3)(
    		valueAt(p+ep.xyy, buf, sz) - valueAt(p-ep.xyy, buf, sz),
            	valueAt(p+ep.yxy, buf, sz) - valueAt(p-ep.yxy, buf, sz),
            	valueAt(p+ep.yyx, buf, sz) - valueAt(p-ep.yyx, buf, sz)));
}
*/
float3 normalAt(const float3 p, read_only image3d_t buf) {
    return normalize(ep.xyy * valueAt(p + ep.xyy, buf)
                   + ep.yyx * valueAt(p + ep.yyx, buf)
                   + ep.yxy * valueAt(p + ep.yxy, buf)
                   + ep.xxx * valueAt(p + ep.xxx, buf));
}
// -------------------------------------------------------------------

inline float hash1(const float seed) {
    float fractptr;
    return fract(sin(seed)*43758.5453123f, &fractptr);
}
inline float2 hash2(const float seed) {
    float2 fractptr;
    return fract(sin((float2)(seed*43758.5453123f,(seed+.1)*22578.1459123f)), &fractptr);
}
inline float3 hash3(const float seed) {
    float3 fractptr;
    return fract(sin((float3)(seed,seed+.1f,seed+.2f))*(float3)(43758.5453123f,22578.1459123f,19642.3490423f), &fractptr);
}

inline float3 randomCosWeightedHemiSphereVector(const float3 n, const float seed) {
    const float r1 = 2.f*M_PI_F*hash1(seed), r2 = hash1(seed+.1f);
    const float3 u = normalize(cross(n, (float3)(0.f,1.f,1.f))), v = cross(u,n);
    return normalize(sqrt(r2)*(cos(r1)*u + sin(r1)*v) + sqrt(1.f-r2)*n);
}

inline float3 randomSphereDirection(const float seed) {
    float2 r = 2.f*M_PI_F*hash2(seed);
    return (float3)(sin(r.x)*(float2)(sin(r.y),cos(r.y)),cos(r.x));
}

inline float3 randomHemisphereDirection(const float3 n, const float seed) {
    float2 r = 2.f*M_PI_F*hash2(seed);
    float3 dr =  (float3)(sin(r.x)*(float2)(sin(r.y),cos(r.y)),cos(r.x));
    float k = dot(dr,n);
    return k == 0.f ? n : normalize(k*dr);
}

// -----------------------------------------------------------------

// fonctionne dans le cas d'une pente
float preciseSurface(const float3 ro, const float3 rd, float val, float dmin, float dmax, float sig, const int dPrec, read_only image3d_t buf) {
    float dm = dmin;
    val *= sig;  
    for (int j=0; j<dPrec;j++) {
       dm = (dmin + dmax)*.5f;  // on détermine l'indice de milieu
       if (valueAt(ro+rd*dm, buf)*sig > val) dmax = dm;  //si la valeur qui est à la case "im" est supérieure à la valeur recherchée, l'indice de fin "ifin" << devient >> l'indice de milieu
       else dmin = dm;               //sinon l'indice de début << devient >> l'indice de milieu et l'intervalle est de la même façon restreint
    }
    return dm;  
}

float findPosOfVal(const float3 ro, const float3 rd, const float val, const float dd, const float dmin, const float dmax, const int dPrec, read_only image3d_t buf) {
    float v0, v, d = dmin+dd;  
    const float3 p = ro+rd*dmin;
    const float sig = valueAt(p, buf) > val ? -1.f: 1.f;
    const float rand = .001f+dd*hash1(rd.y*(p.x+p.y+p.z));
    for(d = dmin+rand; d<dmax+dd; d+=dd){
        v = valueAt(ro+rd*d, buf);
        if (sig*(v-val)>0.f) break;
    }
    if (sig*(v-val) > 0.f && dPrec)
        return preciseSurface(ro, rd, val, d-dd, d, sig, dPrec, buf);
    return d;
}


// -----------------------------------------------------------------

float doAmbiantOcclusion(const float3 ro, const float3 n, const int nbSample, const float dt, const float dmax, const float4 boundsMin, const float4 boundsMax, read_only image3d_t buf) {
    float ao = 0.f;
    float3 p = ro;
    float3 rd;
  //  const float dt=dd*.2f, dmax = 3.f;
    float val;
    float seed = ro.x+ro.y+ro.z;
#if AO_NB_SAMPLE>1
    for (int sa=0;sa<AO_NB_SAMPLE; sa++) {
#endif
        rd = randomHemisphereDirection(n, seed);       
        seed += .1f;
        // suivre cette direction et ajouter un truc a ao si on rtouve un obstacle avant dMax
        for(float t=dt; t<dmax; t+=dt) {
            p = ro + rd * t;
            if (p.x<= boundsMin.x || p.y<= boundsMin.y  || p.z<= boundsMin.z ||
                p.x>= boundsMax.x || p.y>= boundsMax.y  || p.z>= boundsMax.z) {
                    break;
            }
            val = valueAt(p, buf);
            if (val > boundsMin.w && val < boundsMax.w) {
                // on a rencontré un obstacle,
                ao += (clamp((dmax-t)/dmax,0.f,1.f))/(float)AO_NB_SAMPLE; // plus il est pres plus il a de l'influence
             //   if (ao > 1.) return 0.;
                break; // on peu tester un nouveau rayon
            }
        }
#if AO_NB_SAMPLE>1
    }
#endif
    return (1.-ao);
}

float doShadow(const float3 p0, const float3 n, const float dd, const float dMax, const float4 boundsMin, const float4 boundsMax, read_only image3d_t buf) {
    float3 pt;
    float val;
    // suivre cette direction et ajouter un truc si on trouve un obstacle avant dMax
    for(float d = dd; d<dMax; d+=dd) {
       pt = p0 + d*n;
       if (pt.x<=boundsMin.x || pt.y<=boundsMin.y || pt.z<=boundsMin.z ||
           pt.x>=boundsMax.x || pt.y>=boundsMax.y || pt.z>=boundsMax.z) 
            return 1.f;  // hors de la zone de filtrage
       val = valueAt(pt, buf);
       if (val > boundsMin.w &&  val < boundsMax.w) { // on a rencontré un obstacle
           return 1. - clamp((dMax-d)/dMax,0.f,1.f); // invMix(dMax,0.f,d); // plus il est pres plus il a de l'influence
       }
    }
    return 1.f;
}


float4 doShading(const float3 rd, const float3 p, const float3 n, const float3 LIGHT, const float4 col, const float4 boundsMin, const float4 boundsMax, read_only image3d_t buf, const Quality* quality) { 
    const float rimMatch =  1.f - max( 0.f , dot( n , -rd ) );
    const float3 rimCol  = (float3)(.4f, 1.f, 1.f)*rimMatch;

    float ao = 1.f, specular;
    const float dd = quality->dd;
    const float diffuse  = max(0.f, dot(n, LIGHT));
    if (diffuse>0.f) {
        float3 p2 = p-rd*dd*.3f;
#ifdef SHADOW        
        if (quality->sh_on) {
            ao = doShadow(p2, LIGHT, quality->sh_dd, quality->sh_dmax, boundsMin, boundsMax, buf);
            ao = sqrt(ao);
        }
#endif
        specular = max(0.f, dot(LIGHT, reflect(rd, n)));
        specular = pow(specular, 60.f);              //  ao = Math.sqrt(ao);
    } else {
        ao = 1.f - clamp((3.f - 80.f)/(-80.f),0.f,1.f); //invMix(80,0,3);  // dans l ombre
        specular = 0.f;
    }
    const float ambiant = .5f;
    float3 c = (.5f+(.5f*ao)) * (col.xyz*(ambiant + diffuse*.25f) + specular*.2f) + .3f*rimCol;	 
    
#ifdef AO
    if (quality->ao_nb) {
        const float aao = doAmbiantOcclusion(p+n*dd*.1f, n, quality->ao_nb, quality->ao_dd, quality->ao_dmax, boundsMin, boundsMax, buf);
        c *= aao;
    }
#endif
    return (float4)(c, 1.f);

}

float3 heatmapGradient(const float t) {
    return clamp((pow(t, 1.5f) * .8f + .2f) * (float3)(smoothstep(0.f, .35f, t) + t * .5f, smoothstep(.5f, 1.f, t), max(1.f - t * 1.7f, t * 7.f - 6.f)), 0.f, 1.f);
}
/*
float4 getColor(const float valId) {
    const float d = clamp(valId/400.f,0.f,1.f);
    return (valId>=MinVal && valId<=MaxVal) ? (valId<600.?(float4)(d,d,d*.67f,1.f): (float4)(d*d,d*.8,d*.3f,1.f)) : (float4)(0.);
}
*/
inline float4 getColor(const float valId, const float min, const float max) {
	
     float d = clamp(valId/400.f,0.f,1.f);
	//d = sqrt(d);
//	return (float4)(d,d,d,1.f);

    return (valId>=min && valId<=max) ? (
            valId<-308.f?(float4)(0.f,0.f,1.f,1.f)*map(-1000.f, -308.f, 0.f,1.f, valId):   // air
            valId<-180.f?(float4)(0.f,1.f,1.f,1.f):                                        // poumon
            valId<-17.f?(float4)(.95f,1.f,.7f,1.f)*map(-180.f, -17.f, .8f,1.f, valId):     // graisses
            valId<16.f?(float4)(1.f,1.f,.7f,1.f):                                          // eau
            valId<70.f?(float4)(1.f,.5f,.5f,1.f)*map(16.f, 70.f, .4f,1.f, valId):          // muscle
            valId<160.f?(float4)(1.f,.9f,1.f,1.f)*map(70.f, 160.f, .2f,1.f, valId):	   // foie
            valId<260.f?(float4)(1.f,1.f,.7f,1.f)*map(160.f, 260.f, .85f,1.f, valId):       // tissus mou
            valId<516.f?(float4)(.8f,.8f,.6f,1.f)*map(260.f, 516.f, .5f,1.f, valId):                                         // os spongieux
		        (float4)(.75f,.55f,.3f,1.f)) : (float4)(1.f);                       // os

//        Double[] values = {-1000., -308., -180., -17., 16., 70., 165., 230., 516., max};
        //                    Air,             poumon,                    graisses,                     eau,             muscle,      foie,       tisuss mous,          os spongieux,    os
//        Color[] colors = {new (float4)(0), (float4)(0.,1.,1.,1), (float4)(0,0,10,1), new (float4)(1,1,.7,1), (float4)(1.,.1,.1,1), (float4)(1.,.4,.4,1), (float4)(.4,.4,.4,1),  };
}

int box(const float3 ro, const float3 rd, const float3 sz, float* tN, float* tF, float3* n) {
    const float3 m = 1.f/rd,  k = fabs(m)*sz,  a = -m*ro-k*.5f, b = a+k;
    *n = -sign(rd) * step(a.yzx,a.xyz) * step(a.zxy,a.xyz);
    *tN = max(max(a.x,a.y),a.z);
    *tF = min(min(b.x,b.y),b.z);
    return *tN<*tF && *tF>0. ? 1 : 0;
}


float3 RD(const float3 ro, const float3 ta, const float x, const float y, const int2 res, const float h) {
    const float2 resF = convert_float2(res);
    const float px = (2.f * (x/resF.x) - 1.f) * resF.x/resF.y, 
                py = (2.f * (y/resF.y) - 1.f);  
    const float3 
        ww = normalize(ta - ro),
        uu = normalize(cross(ww, (float3)(0.f,1.f,0.f))), // up
        vv = normalize(cross(uu,ww));
    return normalize( px*uu + py*vv + h*ww );
}
void RD_Anaglyph_OffAxis(const float3 ro, const float3 ta, const float x, const float y, const int2 res, const float h, const float distance, const float dv, 
                   float3* roR, float3* roL, float3* rdR, float3* rdL) {
    const float2 resF = convert_float2(res);
    const float px = (2.f * (x / resF.x) - 1.f) * resF.x/resF.y,
                py = (2.f * (y / resF.y) - 1.f);  
    // camera matrix
    const float3 
        ww = normalize(ta - ro),
        uu = normalize(cross(ww,(float3)(0.f,1.f,0.f)) ), // up
        vv = normalize(cross(uu,ww));
    const float3 rd = normalize( px*uu + py*vv + h*ww );
    const float k = distance / dot(ww,rd);
    const float3 pt = ro+k*rd;
    *roR = ro + uu*dv;
    *roL = ro - uu*dv;
    *rdR = normalize(pt - *roR);
    *rdL = normalize(pt - *roL);
}

void RD_Anaglyph_ToeIn(const float3 ro, const float3 ta, const float x, const float y, const int2 res, const float h, const float distance, const float dv, 
                   float3* roR, float3* roL, float3* rdR, float3* rdL) {
    const float3 
        ww = normalize(ta - ro),
        uu = normalize(cross(ww,(float3)(0.f,1.f,0.f)) );        
    const float3 ta0 = ro + ww*distance;
    *roR = ro + uu*dv;
    *roL = ro - uu*dv;
    *rdR = RD(*roR, ta0, x, y, res, h);
    *rdL = RD(*roL, ta0, x, y, res, h);
}


float4 renderRay(const float3 ro, const float3 rd, const float4 sliderMin, const float4 sliderMax, read_only image3d_t buf, const float3 cback, const Quality* quality) {
    const int3 bufSz = get_image_dim(buf).xyz;                  

    const float4 bbMin = mix((float4)(2.f,2.f,2.f/H_COEFF,-1000.f), (float4)(bufSz.x-3, bufSz.y-3,(float)(bufSz.z-2)/H_COEFF,1000.f), sliderMin),
                 bbMax = mix((float4)(2.f,2.f,2.f/H_COEFF,-1000.f), (float4)(bufSz.x-3, bufSz.y-3,(float)(bufSz.z-2)/H_COEFF,1000.f), sliderMax);
    const float3 Bounds = bbMax.xyz - bbMin.xyz; 
 
   const float3 center = .5f*(bbMax.xyz+bbMin.xyz);
    const float3 COLOR_BACK = (float3)(.22f,.26f,.28f);   

    float4 col = (float4)(cback,1.f);

    const float3 lightDir = normalize(normalize(ro-center)*5.f -7.f*cross((float3)(0,0,1.f), normalize(ro-center)));
    
    //const float3 LIGHT_DIR = -normalize((float3)(5.f,-5.f,2.5f));
    
    float3 n,p;
    float d, dmin, dmax;
    float val;
    
    if (box(ro-center, rd, Bounds, &dmin, &dmax, &n)) {
        d = dmin;
       // coupe du rectangle externe
        p = ro+rd*(d-.1f);
        val = valueAt(p, buf);
        col = getColor(val, bbMin.w, bbMax.w);

        if (val > bbMin.w && val <= bbMax.w) {
            col = doShading(rd,p,n,lightDir, col, bbMin, bbMax, buf, quality);
        } else {    
            d = findPosOfVal(ro, rd, val<bbMin.w ? bbMin.w : bbMax.w, quality->dd, d, dmax, quality->dPrec, buf);
            if (d<dmax) {
                p = ro+rd*d;
                n = (val<bbMin.w?-1.f:1.f)*normalAt(p, buf);
                col = getColor(val<bbMin.w ? bbMin.w : bbMax.w, bbMin.w, bbMax.w);
//col *= col;
                col = doShading(rd,p,n,lightDir, col, bbMin, bbMax, buf, quality);
              //  col.xyz = mix(col.xyz, cback, clamp(((d-dmin)/400.f)*(d-dmin)/400.f, 0.f, 1.f));

    // Blend in a bit of logic-defying fog for atmospheric effect. :)
    col.xyz = mix(col.xyz, cback, .15f*smoothstep(0.f, .95f, (d-dmin)/(dmax-dmin))); // exp(-.002*t*t), etc.

            } else {
		col.xyz = cback;            	
            }
        }
    }
    return col;
}

__kernel void render(const float3 roo, const float3 ta, const float4 sliderMin, const float4 sliderMax, read_only image3d_t buf, const int3 ee, write_only image2d_t outputImage, const float2 deltaPix, const int qual) {
    const int x = get_global_id(0);
    const int y = get_global_id(1);
    const int2 outSize = get_image_dim(outputImage);
               
    if (x>=outSize.x || y>=outSize.y) return;

    const int3 bufSz = get_image_dim(buf).xyz;       
    const float4 bbMin = mix((float4)(2.f,2.f,3.f/H_COEFF,-1000.f), (float4)(bufSz.x-3, bufSz.y-3,(float)(bufSz.z-3)/H_COEFF,1000.f), sliderMin),
                 bbMax = mix((float4)(2.f,2.f,3.f/H_COEFF,-1000.f), (float4)(bufSz.x-3, bufSz.y-3,(float)(bufSz.z-3)/H_COEFF,1000.f), sliderMax);

    const float3 Bounds = bbMax.xyz - bbMin.xyz; 
    const float3 center = .5f*(bbMax.xyz+bbMin.xyz);
    const float3 COLOR_BACK = (float3)(.922f,.926f,.928f);   
    
    const float2 q = ((float2)(x,y)+deltaPix)/convert_float2(outSize);
    const float3 cback = COLOR_BACK * pow(16.f*q.x*q.y*(1.f-q.x)*(1.f-q.y),.3f);

    const Quality quality = (qual == 1 ? hq : lq);
    
    float s = 1.f;  
    const float3 ro = roo - ta + center;
#ifdef ANAGLYPH
    float3 roR, roL, rdR, rdL; 
    RD_Anaglyph(ro, center, (float)(x)+deltaPix.x, (float)(y)+deltaPix.y, outSize, 4.5f, 1000.f, -11.f, &roR, &roL, &rdR, &rdL);
    float4 colR = renderRay(roR, rdR, sliderMin, sliderMax, buf, bufSz, cback, &quality);   
    float4 colL = renderRay(roL, rdL, sliderMin, sliderMax, buf, bufSz, cback, &quality);
    float r = .3f*(colR.x + colR.y + colR.z);
    float l = .3f*(colL.x + colL.y + colL.z);

    float4 col = (float4)(colR.x*.2f+r, colL.x*.2f + l, colL.x*.2f + l, 1.f);
#else
    const float3 rd = RD(ro, center, (float)(x)+deltaPix.x, (float)(y)+deltaPix.y, outSize, 3.5f);
    float4 col = renderRay(ro, rd, sliderMin, sliderMax, buf, cback, &quality);   
#endif
    col = clamp(col, (float4)(0.f), (float4)(1.f));
    
   // col = sqrt(col);
#ifdef TEXTURE_OUT_FLOAT
    write_imagef(outputImage, (int2)(x, y), out); 
#else
    uint4 rgba = (uint4)((int)(col.z*256.f),
                         (int)(col.y*256.f),
                         (int)(col.x*256.f), 256);
    write_imageui(outputImage, (int2)(x, y), rgba); 
#endif

}


//__kernel void dummy(const float3 roo) {
//    
//}


