# The ONE Simulator

The Opportunistic Network Environment simulator.<br/>
University College London 
3rd Year BEng Project.

Title: Forwarding Strategies for Information-Centric Devices-to-Device Networks
2020.

Inheriting the works from the senior student's project below (2019):

Title: Buffer Management Strategies in Opportunistic Device-to-Device Networks

In this project, we will implement and explore <b>buffer management strategies</b> for devices in opportunistic D2D networks. The focus in this project is the <i>"ICN_FIFO_Router"</i> within the <i>"router"</i> folder where I implemented an <b>Information Centric Networking (ICN)</b> methodology. The ICN methodology involves having small interest packet looking for data packets instead of having the router spread or flood data packets everywhere regardless of whether nodes have interest in them.

How it works: When a node first receives an interest packet, it...<br/>
1) Checks whether interested packet is in the buffer<br/>
2) If yes, prioritise the interested packet to be sent back to the original host when the connection is stil up!
